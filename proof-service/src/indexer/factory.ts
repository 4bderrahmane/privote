import { getAddress } from "viem"
import { electionFactoryContract, client } from "../domain/chain"
import { env } from "../config/env"
import {
    deleteFactoryDiscoveredElectionsFromBlock,
    getFactorySyncCursor,
    setFactorySyncCursor,
    upsertElectionAddress,
    withTransaction
} from "../infrastructure/db"

const DEFAULT_POLL_MS = 2000
const REORG_BACKOFF_MS = 250
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

export function computeFactoryFinalizedBlock(headBlock: bigint, confirmations: bigint) {
    return headBlock > confirmations ? headBlock - confirmations : 0n
}

export function computeFactoryNextFromCursor(cursor: { blockNumber: bigint; logIndex: bigint }) {
    const isUninitialized =
        cursor.blockNumber === 0n &&
        cursor.logIndex === -1n &&
        (!("blockHash" in cursor) || (cursor as { blockHash?: string | null }).blockHash == null)
    return isUninitialized ? 0n : cursor.blockNumber + 1n
}

export function computeFactoryReorgRewindPlan(cursorBlockNumber: bigint, confirmations: bigint): {
    rewindFrom: bigint
    cursorAfterRewind: { blockNumber: bigint; logIndex: bigint; blockHash: string | null }
} {
    const safety = confirmations + 2n
    const rewindFrom = cursorBlockNumber > safety ? cursorBlockNumber - safety : 0n
    const cursorAfterRewind =
        rewindFrom === 0n
            ? { blockNumber: 0n, logIndex: -1n, blockHash: null }
            : { blockNumber: rewindFrom - 1n, logIndex: -1n, blockHash: null }

    return { rewindFrom, cursorAfterRewind }
}

function normalizeAddress(address: string): `0x${string}` {
    return getAddress(address).toLowerCase() as `0x${string}`
}

type SortableLog = {
    blockNumber: bigint
    logIndex: number | bigint
}

export function sortFactoryLogs<T extends SortableLog>(logs: T[]): T[] {
    logs.sort((a, b) => {
        const byBlock = cmpBigInt(a.blockNumber, b.blockNumber)
        if (byBlock !== 0) return byBlock
        return cmpBigInt(BigInt(a.logIndex), BigInt(b.logIndex))
    })
    return logs
}

export type FactoryElectionDeployedLog = {
    blockNumber: bigint
    logIndex: number | bigint
    args: {
        election?: string
    }
}

export function buildDiscoveredElectionBatch(logs: FactoryElectionDeployedLog[]) {
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

    return { discovered, lastLogIndex }
}

export type FactoryReorgDeps = {
    getFactorySyncCursor: typeof getFactorySyncCursor
    getBlockByNumber: (blockNumber: bigint) => Promise<{ hash: `0x${string}` }>
    withTransaction: typeof withTransaction
    deleteFactoryDiscoveredElectionsFromBlock: (
        fromBlock: bigint,
        tx: Parameters<Parameters<typeof withTransaction>[0]>[0]
    ) => Promise<void>
    setFactorySyncCursor: typeof setFactorySyncCursor
}

const defaultFactoryReorgDeps: FactoryReorgDeps = {
    getFactorySyncCursor,
    getBlockByNumber: async (blockNumber) => client.getBlock({ blockNumber }),
    withTransaction,
    deleteFactoryDiscoveredElectionsFromBlock: async (fromBlock, tx) =>
        deleteFactoryDiscoveredElectionsFromBlock(fromBlock, tx),
    setFactorySyncCursor
}

export async function handleFactoryReorgIfDetected(
    factoryAddress: `0x${string}`,
    confirmations: bigint,
    startBlock: bigint,
    deps: FactoryReorgDeps = defaultFactoryReorgDeps
) {
    const cursor = await deps.getFactorySyncCursor(factoryAddress, startBlock)

    if (cursor.blockNumber > 0n && cursor.blockHash) {
        const canonical = await deps.getBlockByNumber(cursor.blockNumber)

        if (canonical.hash !== cursor.blockHash) {
            const { rewindFrom, cursorAfterRewind } = computeFactoryReorgRewindPlan(
                cursor.blockNumber,
                confirmations
            )

            await deps.withTransaction(async (tx) => {
                await deps.deleteFactoryDiscoveredElectionsFromBlock(rewindFrom, tx)
                await deps.setFactorySyncCursor(factoryAddress, cursorAfterRewind, tx)
            })

            return { didReorg: true, cursor }
        }
    }

    return { didReorg: false, cursor }
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
            const finalizedBlock = computeFactoryFinalizedBlock(headBlock, confirmations)

            const reorgResult = await handleFactoryReorgIfDetected(
                factoryAddress,
                confirmations,
                env.FACTORY_START_BLOCK
            )
            if (reorgResult.didReorg) {
                await sleep(REORG_BACKOFF_MS)
                continue
            }

            const cursor = reorgResult.cursor
            let from = computeFactoryNextFromCursor(cursor)

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
                sortFactoryLogs(logs)

                const { discovered, lastLogIndex } = buildDiscoveredElectionBatch(logs)
                const toBlockHash = (await client.getBlock({ blockNumber: to })).hash
                const newlyDiscovered: `0x${string}`[] = []

                await withTransaction(async (tx) => {
                    for (const [election, cursorPos] of discovered) {
                        const inserted = await upsertElectionAddress(
                            election,
                            {
                                source: "factory",
                                blockNumber: cursorPos.blockNumber,
                                logIndex: cursorPos.logIndex
                            },
                            tx
                        )
                        if (inserted) newlyDiscovered.push(election)
                    }

                    await setFactorySyncCursor(
                        factoryAddress,
                        { blockNumber: to, logIndex: lastLogIndex, blockHash: toBlockHash },
                        tx
                    )
                })

                if (onElectionDiscovered) {
                    for (const election of newlyDiscovered) {
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
