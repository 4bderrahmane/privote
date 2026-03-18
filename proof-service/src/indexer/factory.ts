import { getAddress } from "viem"
import { electionFactoryContract, client } from "../domain/chain"
import { env } from "../config/env"
import {
    getFactorySyncCursor,
    setFactorySyncCursor,
    upsertElectionAddress,
    withTransaction
} from "../infrastructure/db"

const DEFAULT_POLL_MS = 2000
const ERROR_BACKOFF_MS = 3000

type FactoryDiscoveryOptions = {
    signal?: AbortSignal
    onElectionDiscovered?: (election: `0x${string}`) => Promise<void> | void
}

function sleep(ms: number) {
    return new Promise((res) => setTimeout(res, ms))
}

function minBigInt(a: bigint, b: bigint) {
    return a < b ? a : b
}

function cmpBigInt(a: bigint, b: bigint): number {
    if (a < b) return -1
    if (a > b) return 1
    return 0
}

function computeFinalizedBlock(headBlock: bigint, confirmations: bigint) {
    return headBlock > confirmations ? headBlock - confirmations : 0n
}

function computeNextFromCursor(cursor: { blockNumber: bigint; logIndex: bigint }) {
    const isUninitialized = cursor.blockNumber === 0n && cursor.logIndex === -1n
    return isUninitialized ? 0n : cursor.blockNumber + 1n
}

function normalizeAddress(address: string): `0x${string}` {
    return getAddress(address).toLowerCase() as `0x${string}`
}

type SortableLog = {
    blockNumber: bigint
    logIndex: number | bigint
}

function sortLogs<T extends SortableLog>(logs: T[]): T[] {
    logs.sort((a, b) => {
        const byBlock = cmpBigInt(a.blockNumber, b.blockNumber)
        if (byBlock !== 0) return byBlock
        return cmpBigInt(BigInt(a.logIndex), BigInt(b.logIndex))
    })
    return logs
}

export async function runFactoryElectionDiscoveryLoop(
    factoryAddress: `0x${string}`,
    opts: FactoryDiscoveryOptions = {}
) {
    const { signal, onElectionDiscovered } = opts

    const contract = electionFactoryContract(factoryAddress)
    const confirmations = BigInt(env.CONFIRMATIONS ?? 0)
    const batchSize = BigInt(env.LOG_BATCH_SIZE ?? 50_000)

    for (;;) {
        if (signal?.aborted) return

        try {
            const headBlock = await client.getBlockNumber()
            const finalizedBlock = computeFinalizedBlock(headBlock, confirmations)

            const cursor = await getFactorySyncCursor(factoryAddress, env.FACTORY_START_BLOCK)
            let from = computeNextFromCursor(cursor)

            if (from > finalizedBlock) {
                await sleep(DEFAULT_POLL_MS)
                continue
            }

            while (from <= finalizedBlock) {
                if (signal?.aborted) return

                const to = minBigInt(finalizedBlock, from + batchSize - 1n)

                const logs = await contract.getEvents.ElectionDeployed(
                    {},
                    { fromBlock: from, toBlock: to }
                )
                sortLogs(logs)

                const discovered = new Map<`0x${string}`, { blockNumber: bigint; logIndex: bigint }>()
                let lastLogIndex = -1n

                for (const log of logs) {
                    lastLogIndex = BigInt(log.logIndex)

                    const election = log.args.election
                    if (!election) continue

                    discovered.set(normalizeAddress(election), {
                        blockNumber: log.blockNumber,
                        logIndex: BigInt(log.logIndex)
                    })
                }

                await withTransaction(async (tx) => {
                    for (const [election, cursorPos] of discovered) {
                        await upsertElectionAddress(
                            election,
                            {
                                source: "factory",
                                blockNumber: cursorPos.blockNumber,
                                logIndex: cursorPos.logIndex
                            },
                            tx
                        )
                    }

                    await setFactorySyncCursor(
                        factoryAddress,
                        { blockNumber: to, logIndex: lastLogIndex },
                        tx
                    )
                })

                if (onElectionDiscovered) {
                    for (const election of discovered.keys()) {
                        await onElectionDiscovered(election)
                    }
                }

                from = to + 1n
            }
        } catch (err) {
            console.error(`[factory-indexer] ${factoryAddress} error`, err)
            await sleep(ERROR_BACKOFF_MS)
        }
    }
}
