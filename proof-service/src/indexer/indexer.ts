import { getSyncCursor, setSyncCursor, upsertMember, withTransaction } from "../infrastructure/db"
import type { ElectionGroupState } from "../domain/state"
import { bootstrapElectionSnapshot } from "./bootstrap"
import { client, electionContract } from "../domain/chain"
import { env } from "../config/env"

const DEFAULT_POLL_MS = 2000
const REORG_BACKOFF_MS = 250
const ERROR_BACKOFF_MS = 3000

type IndexerOptions = {
    signal?: AbortSignal
}

function sleep(ms: number) {
    return new Promise((res) => setTimeout(res, ms))
}

function minBigInt(a: bigint, b: bigint) {
    if (a === b) return a
    return a < b ? a : b
}

function cmpBigInt(a: bigint, b: bigint): number {
    if (a < b) return -1
    if (a > b) return 1
    return 0
}

export type CursorState = {
    blockNumber: bigint
    logIndex: bigint
    blockHash: string | null
}

export function computeFinalizedBlock(headBlock: bigint, confirmations: bigint) {
    return headBlock > confirmations ? headBlock - confirmations : 0n
}

export function computeNextFromCursor(cursor: CursorState) {
    const isUninitialized =
        cursor.blockNumber === 0n &&
        cursor.logIndex === -1n &&
        cursor.blockHash === null

    return isUninitialized ? 0n : cursor.blockNumber + 1n
}

export function sortMemberAddedLogs<T extends { blockNumber: bigint; logIndex: number | bigint }>(logs: T[]): T[] {
    logs.sort((a, b) => {
        const byBlock = cmpBigInt(a.blockNumber, b.blockNumber)
        if (byBlock !== 0) return byBlock
        return cmpBigInt(BigInt(a.logIndex), BigInt(b.logIndex))
    })
    return logs
}

export function computeReorgRewindPlan(cursorBlockNumber: bigint, confirmations: bigint): {
    rewindFrom: bigint
    cursorAfterRewind: CursorState
} {
    const safety = confirmations + 2n
    const rewindFrom = cursorBlockNumber > safety ? cursorBlockNumber - safety : 0n
    const cursorAfterRewind =
        rewindFrom === 0n
            ? { blockNumber: 0n, logIndex: -1n, blockHash: null }
            : { blockNumber: rewindFrom - 1n, logIndex: -1n, blockHash: null }

    return { rewindFrom, cursorAfterRewind }
}

function assertSafeIndex(index: bigint) {
    if (index > BigInt(Number.MAX_SAFE_INTEGER)) {
        throw new Error("leafIndex too large for JS number")
    }
}

/**
 * Rebuild in-memory state from DB snapshot (used after reorgs or to recover from inconsistencies).
 */
async function rebuildStateFromDb(state: ElectionGroupState) {
    const snapshot = await bootstrapElectionSnapshot(state.election)
    state.init(snapshot.state.groupId, snapshot.state.expectedDepth)
    state.rebuildFromMembers(snapshot.members)
}

export type ReorgDeps = {
    getSyncCursor: typeof getSyncCursor
    getBlockByNumber: (blockNumber: bigint) => Promise<{ hash: `0x${string}` }>
    withTransaction: typeof withTransaction
    deleteMembersFromBlock: (
        tx: Parameters<Parameters<typeof withTransaction>[0]>[0],
        election: string,
        fromBlock: bigint
    ) => Promise<void>
    setSyncCursor: typeof setSyncCursor
    rebuildStateFromDb: (state: ElectionGroupState) => Promise<void>
}

const defaultReorgDeps: ReorgDeps = {
    getSyncCursor,
    getBlockByNumber: async (blockNumber) => client.getBlock({ blockNumber }),
    withTransaction,
    deleteMembersFromBlock: async (tx, election, fromBlock) => {
        await tx.query(
            `DELETE FROM members WHERE election_address=$1 AND block_number >= $2`,
            [election, fromBlock.toString()]
        )
    },
    setSyncCursor,
    rebuildStateFromDb
}

/**
 * Detect reorg by comparing stored cursor hash with current canonical hash.
 * If detected, rewind conservatively and rebuild state.
 */
export async function handleReorgIfDetected(
    state: ElectionGroupState,
    confirmations: bigint,
    deps: ReorgDeps = defaultReorgDeps
) {
    const cursor = await deps.getSyncCursor(state.election)

    if (cursor.blockNumber > 0n && cursor.blockHash) {
        const blk = await deps.getBlockByNumber(cursor.blockNumber)
        if (blk.hash !== cursor.blockHash) {
            const { rewindFrom, cursorAfterRewind } = computeReorgRewindPlan(cursor.blockNumber, confirmations)

            await deps.withTransaction(async (tx) => {
                await deps.deleteMembersFromBlock(tx, state.election, rewindFrom)

                await deps.setSyncCursor(state.election, cursorAfterRewind, tx)
            })

            await deps.rebuildStateFromDb(state)
            return { didReorg: true, cursor }
        }
    }

    return { didReorg: false, cursor }
}

export type PendingAdd = {
    groupId: bigint
    leafIndex: number
    commitment: bigint
    blockNumber: bigint
    logIndex: bigint
}

/**
 * Build and validate a deterministic batch of "add member" operations from logs.
 * IMPORTANT: does NOT mutate in-memory state.
 */
export function buildPendingAdds(state: ElectionGroupState, logs: any[]): PendingAdd[] {
    const pending: PendingAdd[] = []

    let expected = state.size

    for (const log of logs) {
        const { groupId, index, identityCommitment } = log.args as {
            groupId: bigint
            index: bigint
            identityCommitment: bigint
        }

        const leafIndexBI = BigInt(index)
        assertSafeIndex(leafIndexBI)

        const leafIndex = Number(leafIndexBI)
        if (leafIndex !== expected) {
            throw new Error(`Out-of-order insert: got index=${leafIndex}, expected=${expected}`)
        }
        expected++

        pending.push({
            groupId: BigInt(groupId),
            leafIndex,
            commitment: BigInt(identityCommitment),
            blockNumber: log.blockNumber,
            logIndex: BigInt(log.logIndex)
        })
    }

    return pending
}

type PersistBatchDeps = {
    withTransaction: typeof withTransaction
    upsertMember: typeof upsertMember
    setSyncCursor: typeof setSyncCursor
}

const defaultPersistBatchDeps: PersistBatchDeps = {
    withTransaction,
    upsertMember,
    setSyncCursor
}

export async function persistBatch(
    state: ElectionGroupState,
    pending: PendingAdd[],
    toBlockNumber: bigint,
    toBlockHash: `0x${string}`,
    deps: PersistBatchDeps = defaultPersistBatchDeps
) {
    const lastLogIndex =
        pending.length > 0 ? pending[pending.length - 1].logIndex : -1n

    await deps.withTransaction(async (tx) => {
        for (const a of pending) {
            await deps.upsertMember(
                {
                    election: state.election,
                    groupId: a.groupId.toString(),
                    leafIndex: BigInt(a.leafIndex),
                    commitment: a.commitment.toString(),
                    blockNumber: a.blockNumber,
                    logIndex: a.logIndex
                },
                tx
            )
        }

        await deps.setSyncCursor(
            state.election,
            { blockNumber: toBlockNumber, logIndex: lastLogIndex, blockHash: toBlockHash },
            tx
        )
    })
}

function applyBatchToMemory(state: ElectionGroupState, pending: PendingAdd[]) {
    for (const a of pending) {
        state.addMember(a.commitment, a.leafIndex)
    }
}

/**
 * Continuously indexes MemberAdded logs for one election and keeps:
 *  - DB up to date (members + cursor)
 *  - in-memory ElectionGroupState updated (so /proof is instant)
 *
 * Invariants:
 *  - Processes only up to finalized head (head - confirmations).
 *  - Applies logs in deterministic order (blockNumber, logIndex).
 *  - DB cursor updates are atomic with DB inserts per batch.
 *
 * Safety:
 *  - Never mutates in-memory state inside the DB transaction.
 *  - On reorg or inconsistency, rebuilds from DB.
 */
export async function runIndexerLoop(state: ElectionGroupState, opts: IndexerOptions = {}): Promise<void> {
    const { signal } = opts
    const contract = electionContract(state.election)

    const confirmations = BigInt(env.CONFIRMATIONS ?? 0)
    const batchSize = BigInt(env.LOG_BATCH_SIZE ?? 50_000)

    for (;;) {
        if (signal?.aborted) return

        try {
            const headBlock = await client.getBlockNumber()
            const finalizedBlock = computeFinalizedBlock(headBlock, confirmations)

            const reorgResult = await handleReorgIfDetected(state, confirmations)
            if (reorgResult.didReorg) {
                await sleep(REORG_BACKOFF_MS)
                continue
            }

            const cursor = reorgResult.cursor
            let from = computeNextFromCursor(cursor)

            if (from > finalizedBlock) {
                await sleep(DEFAULT_POLL_MS)
                continue
            }

            while (from <= finalizedBlock) {
                if (signal?.aborted) return

                const to = minBigInt(finalizedBlock, from + batchSize - 1n)

                // Fetch logs for this batch (filter by groupId for efficiency).
                const logs = await contract.getEvents.MemberAdded(
                    { groupId: state.groupId },
                    { fromBlock: from, toBlock: to }
                )

                sortMemberAddedLogs(logs)

                // Validate and prepare pending changes (no state mutation).
                const pending = buildPendingAdds(state, logs)

                // Fetch the canonical block hash OUTSIDE the DB transaction.
                const toBlockHash = (await client.getBlock({ blockNumber: to })).hash

                // Persist DB + cursor atomically.
                await persistBatch(state, pending, to, toBlockHash)

                // Apply to in-memory state after commit.
                try {
                    applyBatchToMemory(state, pending)
                } catch (e) {
                    // If memory got inconsistent for any reason, rebuild from DB snapshot.
                    console.error(`[indexer] ${state.election} memory apply failed, rebuilding`, e)
                    await rebuildStateFromDb(state)
                }

                from = to + 1n
            }
        } catch (err) {
            console.error(`[indexer] ${state.election} error`, err)
            await sleep(ERROR_BACKOFF_MS)
        }
    }
}
