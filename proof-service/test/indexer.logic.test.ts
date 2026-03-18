import test from "node:test"
import assert from "node:assert/strict"
import { ElectionGroupState } from "../src/domain/state"
import {
    buildPendingAdds,
    computeNextFromCursor,
    computeReorgRewindPlan,
    handleReorgIfDetected,
    persistBatch,
    sortMemberAddedLogs,
    type PendingAdd
} from "../src/indexer"

const ELECTION = "0x0000000000000000000000000000000000000001" as const

function newState() {
    const state = new ElectionGroupState(ELECTION)
    state.init(1n, 20)
    return state
}

function makeInMemoryPersistHarness(opts: { failOnUpsertCall?: number } = {}) {
    const members = new Map<string, string>()
    let cursor = { blockNumber: 0n, logIndex: -1n, blockHash: null as string | null }
    let upsertCalls = 0

    const deps = {
        withTransaction: async (fn: (tx: any) => Promise<void>) => {
            const membersSnapshot = new Map(members)
            const cursorSnapshot = { ...cursor }

            try {
                await fn({} as any)
            } catch (err) {
                members.clear()
                for (const [k, v] of membersSnapshot) members.set(k, v)
                cursor = cursorSnapshot
                throw err
            }
        },
        upsertMember: async (params: any) => {
            upsertCalls++
            if (opts.failOnUpsertCall && upsertCalls === opts.failOnUpsertCall) {
                throw new Error("synthetic upsert failure")
            }

            const key = `${params.election}:${params.leafIndex.toString()}`
            const existing = members.get(key)
            if (existing && existing !== params.commitment) {
                throw new Error("Member row conflict")
            }
            members.set(key, params.commitment)
        },
        setSyncCursor: async (_election: string, nextCursor: any) => {
            cursor = nextCursor
        }
    }

    return {
        deps,
        getMembers: () => new Map(members),
        getCursor: () => ({ ...cursor }),
        getUpsertCalls: () => upsertCalls
    }
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

test("fresh start with no cursor starts from block 0 exactly once", async () => {
    const state = newState()
    const harness = makeInMemoryPersistHarness()

    const firstFrom = computeNextFromCursor({
        blockNumber: 0n,
        logIndex: -1n,
        blockHash: null
    })
    assert.equal(firstFrom, 0n)

    await persistBatch(
        state,
        [],
        0n,
        "0xabc" as `0x${string}`,
        harness.deps as any
    )

    const secondFrom = computeNextFromCursor(harness.getCursor())
    assert.equal(secondFrom, 1n)
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
    const beforeSize = state.size

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

    assert.equal(state.size, beforeSize)
})

test("buildPendingAdds rejects skipped indexes and does not advance state", () => {
    const state = newState()
    const beforeSize = state.size

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

    assert.equal(state.size, beforeSize)
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
                const result = await fn({} as any)
                calls.push("commit")
                return result
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
                const result = await fn({} as any)
                calls.push("commit")
                return result
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

test("empty log batches advance cursor so blocks are not reprocessed forever", async () => {
    const state = newState()
    const harness = makeInMemoryPersistHarness()

    await persistBatch(
        state,
        [],
        42n,
        "0xaaa" as `0x${string}`,
        harness.deps as any
    )

    const cursor = harness.getCursor()
    assert.deepEqual(cursor, { blockNumber: 42n, logIndex: -1n, blockHash: "0xaaa" })
    assert.equal(computeNextFromCursor(cursor), 43n)
})

test("non-empty batches persist members and cursor atomically (rollback on failure)", async () => {
    const state = newState()
    const harness = makeInMemoryPersistHarness({ failOnUpsertCall: 2 })
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

    await assert.rejects(
        persistBatch(
            state,
            pending,
            10n,
            "0xdef" as `0x${string}`,
            harness.deps as any
        ),
        /synthetic upsert failure/
    )

    assert.equal(harness.getMembers().size, 0)
    assert.deepEqual(harness.getCursor(), { blockNumber: 0n, logIndex: -1n, blockHash: null })
})

test("restart after partial history resumes from the correct next block", () => {
    const from = computeNextFromCursor({
        blockNumber: 250n,
        logIndex: -1n,
        blockHash: "0xpartial"
    })
    assert.equal(from, 251n)
})

test("reorg detection rewinds cursor, deletes affected members, and prepares replay from rewind point", async () => {
    const state = newState()
    let deletedFrom: bigint | null = null
    let rewoundCursor: any = null
    let rebuilt = false

    const result = await handleReorgIfDetected(
        state,
        5n,
        {
            getSyncCursor: async () => ({
                blockNumber: 20n,
                logIndex: 3n,
                blockHash: "0xoldhash"
            }),
            getBlockByNumber: async () => ({
                hash: "0xnewhash" as `0x${string}`
            }),
            withTransaction: async (fn: (arg0: any) => any) => {
                return fn({} as any)
            },
            deleteMembersFromBlock: async (_tx: any, election: unknown, fromBlock: bigint | null) => {
                assert.equal(election, state.election)
                deletedFrom = fromBlock
            },
            setSyncCursor: async (_election: any, cursor: any) => {
                rewoundCursor = cursor
            },
            rebuildStateFromDb: async () => {
                rebuilt = true
            }
        }
    )

    assert.equal(result.didReorg, true)
    assert.equal(deletedFrom, 13n)
    assert.deepEqual(rewoundCursor, { blockNumber: 12n, logIndex: -1n, blockHash: null })
    assert.equal(computeNextFromCursor(rewoundCursor), 13n)
    assert.equal(rebuilt, true)
})

test("reorg detection does nothing when canonical hash still matches", async () => {
    const state = newState()
    let deleted = false
    let rewound = false
    let rebuilt = false

    const result = await handleReorgIfDetected(
        state,
        5n,
        {
            getSyncCursor: async () => ({
                blockNumber: 20n,
                logIndex: 3n,
                blockHash: "0xsamehash"
            }),
            getBlockByNumber: async () => ({
                hash: "0xsamehash" as `0x${string}`
            }),
            withTransaction: async (fn: (arg0: any) => any) => {
                return fn({} as any)
            },
            deleteMembersFromBlock: async () => {
                deleted = true
            },
            setSyncCursor: async () => {
                rewound = true
            },
            rebuildStateFromDb: async () => {
                rebuilt = true
            }
        }
    )

    assert.equal(result.didReorg, false)
    assert.equal(deleted, false)
    assert.equal(rewound, false)
    assert.equal(rebuilt, false)
})

test("duplicate event replay is idempotent for persistence", async () => {
    const state = newState()
    const harness = makeInMemoryPersistHarness()
    const pending: PendingAdd[] = [
        {
            groupId: 1n,
            leafIndex: 0,
            commitment: 111n,
            blockNumber: 10n,
            logIndex: 1n
        },
        {
            groupId: 1n,
            leafIndex: 1,
            commitment: 222n,
            blockNumber: 10n,
            logIndex: 2n
        }
    ]

    await persistBatch(
        state,
        pending,
        10n,
        "0xabc" as `0x${string}`,
        harness.deps as any
    )
    await persistBatch(
        state,
        pending,
        10n,
        "0xabc" as `0x${string}`,
        harness.deps as any
    )

    assert.equal(harness.getMembers().size, 2)
    assert.deepEqual(harness.getCursor(), { blockNumber: 10n, logIndex: 2n, blockHash: "0xabc" })
    assert.equal(harness.getUpsertCalls(), 4)
})
