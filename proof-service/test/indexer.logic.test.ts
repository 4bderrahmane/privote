import test from "node:test"
import assert from "node:assert/strict"
import { ElectionGroupState } from "../src/domain/state"
import {
    buildPendingAdds,
    computeNextFromCursor,
    computeReorgRewindPlan,
    persistBatch,
    sortMemberAddedLogs,
    type PendingAdd
} from "../src/indexer/indexer"

const ELECTION = "0x0000000000000000000000000000000000000001" as const

function newState() {
    const state = new ElectionGroupState(ELECTION)
    state.init(1n, 20)
    return state
}

test("computeNextFromCursor handles startup and restart cursors", () => {
    assert.equal(
        computeNextFromCursor({ blockNumber: 0n, logIndex: -1n, blockHash: null }),
        0n
    )

    assert.equal(
        computeNextFromCursor({ blockNumber: 0n, logIndex: 0n, blockHash: "0xabc" }),
        1n
    )

    assert.equal(
        computeNextFromCursor({ blockNumber: 25n, logIndex: 3n, blockHash: "0xdef" }),
        26n
    )
})

test("computeReorgRewindPlan rewinds to include deleted blocks", () => {
    const { rewindFrom, cursorAfterRewind } = computeReorgRewindPlan(15n, 5n)

    assert.equal(rewindFrom, 8n)
    assert.deepEqual(cursorAfterRewind, { blockNumber: 7n, logIndex: -1n, blockHash: null })
    assert.equal(computeNextFromCursor(cursorAfterRewind), 8n)
})

test("computeReorgRewindPlan rewinds to block 0 when cursor is in safety window", () => {
    const { rewindFrom, cursorAfterRewind } = computeReorgRewindPlan(3n, 5n)

    assert.equal(rewindFrom, 0n)
    assert.deepEqual(cursorAfterRewind, { blockNumber: 0n, logIndex: -1n, blockHash: null })
    assert.equal(computeNextFromCursor(cursorAfterRewind), 0n)
})

test("sortMemberAddedLogs sorts by blockNumber then logIndex", () => {
    const logs = [
        { blockNumber: 9n, logIndex: 3n },
        { blockNumber: 8n, logIndex: 99n },
        { blockNumber: 9n, logIndex: 1n }
    ]

    sortMemberAddedLogs(logs)
    assert.deepEqual(logs, [
        { blockNumber: 8n, logIndex: 99n },
        { blockNumber: 9n, logIndex: 1n },
        { blockNumber: 9n, logIndex: 3n }
    ])
})

test("buildPendingAdds accepts empty batches without mutating state", () => {
    const state = newState()
    const beforeSize = state.size

    const pending = buildPendingAdds(state, [])

    assert.deepEqual(pending, [])
    assert.equal(state.size, beforeSize)
})

test("buildPendingAdds rejects out-of-order inserts", () => {
    const state = newState()
    state.addMember(11n, 0)

    assert.throws(
        () =>
            buildPendingAdds(state, [
                {
                    args: { groupId: 1n, index: 2n, identityCommitment: 22n },
                    blockNumber: 100n,
                    logIndex: 0n
                }
            ]),
        /Out-of-order insert/
    )
})

test("persistBatch sets cursor log index to -1 for empty batches", async () => {
    const state = newState()
    const calls: string[] = []
    let setCursorPayload:
        | { blockNumber: bigint; logIndex: bigint; blockHash: string | null }
        | undefined

    await persistBatch(
        state,
        [],
        42n,
        "0xabc" as `0x${string}`,
        {
            withTransaction: async (fn) => {
                calls.push("begin")
                await fn({} as any)
                calls.push("commit")
            },
            upsertMember: async () => {
                calls.push("upsert")
            },
            setSyncCursor: async (_election, cursor) => {
                calls.push("cursor")
                setCursorPayload = cursor
            }
        }
    )

    assert.deepEqual(calls, ["begin", "cursor", "commit"])
    assert.deepEqual(setCursorPayload, { blockNumber: 42n, logIndex: -1n, blockHash: "0xabc" })
})

test("persistBatch upserts members before cursor update", async () => {
    const state = newState()
    const calls: string[] = []
    const pending: PendingAdd[] = [
        {
            groupId: 1n,
            leafIndex: 0,
            commitment: 101n,
            blockNumber: 10n,
            logIndex: 2n
        },
        {
            groupId: 1n,
            leafIndex: 1,
            commitment: 202n,
            blockNumber: 10n,
            logIndex: 5n
        }
    ]

    await persistBatch(
        state,
        pending,
        10n,
        "0xdef" as `0x${string}`,
        {
            withTransaction: async (fn) => {
                calls.push("begin")
                await fn({} as any)
                calls.push("commit")
            },
            upsertMember: async () => {
                calls.push("upsert")
            },
            setSyncCursor: async (_election, cursor) => {
                calls.push(`cursor:${cursor.logIndex.toString()}`)
            }
        }
    )

    assert.deepEqual(calls, ["begin", "upsert", "upsert", "cursor:5", "commit"])
})
