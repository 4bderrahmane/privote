import test from "node:test"
import assert from "node:assert/strict"
import {
    buildDiscoveredElectionBatch,
    computeFactoryFinalizedBlock,
    computeFactoryNextFromCursor,
    computeFactoryReorgRewindPlan,
    handleFactoryReorgIfDetected,
    sortFactoryLogs,
    type FactoryElectionDeployedLog
} from "../src/indexer/factory"

const FACTORY = "0x00000000000000000000000000000000000000f1" as const

test("computeFactoryFinalizedBlock respects confirmations", () => {
    assert.equal(computeFactoryFinalizedBlock(20n, 5n), 15n)
    assert.equal(computeFactoryFinalizedBlock(5n, 5n), 0n)
    assert.equal(computeFactoryFinalizedBlock(3n, 10n), 0n)
})

test("computeFactoryNextFromCursor handles startup and restart", () => {
    assert.equal(computeFactoryNextFromCursor({ blockNumber: 0n, logIndex: -1n, blockHash: null }), 0n)
    assert.equal(computeFactoryNextFromCursor({ blockNumber: 0n, logIndex: -1n, blockHash: "0xabc" }), 1n)
    assert.equal(computeFactoryNextFromCursor({ blockNumber: 0n, logIndex: 2n, blockHash: "0xdef" }), 1n)
    assert.equal(computeFactoryNextFromCursor({ blockNumber: 42n, logIndex: 0n, blockHash: "0x123" }), 43n)
})

test("computeFactoryReorgRewindPlan rewinds conservatively", () => {
    const { rewindFrom, cursorAfterRewind } = computeFactoryReorgRewindPlan(20n, 5n)
    assert.equal(rewindFrom, 13n)
    assert.deepEqual(cursorAfterRewind, { blockNumber: 12n, logIndex: -1n, blockHash: null })
})

test("handleFactoryReorgIfDetected rewinds cursor and deletes stale factory elections on hash mismatch", async () => {
    let deletedFrom: bigint | null = null
    let rewoundCursor: { blockNumber: bigint; logIndex: bigint; blockHash: string | null } | null = null

    const result = await handleFactoryReorgIfDetected(FACTORY, 5n, 0n, {
        getFactorySyncCursor: async () => ({
            blockNumber: 20n,
            logIndex: 3n,
            blockHash: "0xoldhash"
        }),
        getBlockByNumber: async () => ({
            hash: "0xnewhash" as `0x${string}`
        }),
        withTransaction: async <T>(fn: (client: any) => Promise<T>): Promise<T> => {
            return fn({} as any)
        },
        deleteFactoryDiscoveredElectionsFromBlock: async (fromBlock) => {
            deletedFrom = fromBlock
        },
        setFactorySyncCursor: async (_factory, cursor) => {
            rewoundCursor = cursor
        }
    })

    assert.equal(result.didReorg, true)
    assert.equal(deletedFrom, 13n)
    assert.deepEqual(rewoundCursor, { blockNumber: 12n, logIndex: -1n, blockHash: null })
})

test("handleFactoryReorgIfDetected is a no-op when canonical hash matches", async () => {
    let deleted = false
    let rewound = false

    const result = await handleFactoryReorgIfDetected(FACTORY, 5n, 0n, {
        getFactorySyncCursor: async () => ({
            blockNumber: 20n,
            logIndex: 3n,
            blockHash: "0xsamehash"
        }),
        getBlockByNumber: async () => ({
            hash: "0xsamehash" as `0x${string}`
        }),
        withTransaction: async <T>(fn: (client: any) => Promise<T>): Promise<T> => {
            return fn({} as any)
        },
        deleteFactoryDiscoveredElectionsFromBlock: async () => {
            deleted = true
        },
        setFactorySyncCursor: async () => {
            rewound = true
        }
    })

    assert.equal(result.didReorg, false)
    assert.equal(deleted, false)
    assert.equal(rewound, false)
})

test("sortFactoryLogs sorts by block then log index", () => {
    const logs = [
        { blockNumber: 5n, logIndex: 9n },
        { blockNumber: 4n, logIndex: 99n },
        { blockNumber: 5n, logIndex: 1n }
    ]

    sortFactoryLogs(logs)

    assert.deepEqual(logs, [
        { blockNumber: 4n, logIndex: 99n },
        { blockNumber: 5n, logIndex: 1n },
        { blockNumber: 5n, logIndex: 9n }
    ])
})

test("buildDiscoveredElectionBatch keeps latest event per election and tracks cursor", () => {
    const logs: FactoryElectionDeployedLog[] = [
        {
            blockNumber: 11n,
            logIndex: 0n,
            args: { election: "0x00000000000000000000000000000000000000aa" }
        },
        {
            blockNumber: 11n,
            logIndex: 1n,
            args: {}
        },
        {
            blockNumber: 12n,
            logIndex: 0n,
            args: { election: "0x00000000000000000000000000000000000000bb" }
        },
        {
            blockNumber: 12n,
            logIndex: 4n,
            args: { election: "0x00000000000000000000000000000000000000aa" }
        }
    ]

    const { discovered, lastLogIndex } = buildDiscoveredElectionBatch(logs)

    assert.equal(discovered.size, 2)
    assert.deepEqual(discovered.get("0x00000000000000000000000000000000000000aa"), {
        blockNumber: 12n,
        logIndex: 4n
    })
    assert.deepEqual(discovered.get("0x00000000000000000000000000000000000000bb"), {
        blockNumber: 12n,
        logIndex: 0n
    })
    assert.equal(lastLogIndex, 4n)
})
