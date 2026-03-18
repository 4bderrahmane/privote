import test from "node:test"
import assert from "node:assert/strict"
import {
    getSyncCursor,
    loadElectionAddresses,
    loadMembers,
    pool,
    seedElectionAddresses,
    setSyncCursor,
    upsertMember,
    withTransaction
} from "../src/infrastructure/db"
import { bootstrapElectionState } from "../src/indexer"
import { buildServer } from "../src/app/server"
import { buildPendingAdds, computeNextFromCursor, persistBatch } from "../src/indexer"

type SyncStateRow = {
    last_processed_block: string
    last_processed_log_index: string
    last_processed_block_hash: string | null
}

type MemberRow = {
    election_address: string
    group_id: string
    leaf_index: string
    identity_commitment: string
    block_number: string
    log_index: string
}

type ElectionRow = {
    election_address: string
    source: "seed" | "factory"
    discovered_block: string
    discovered_log_index: string
    created_seq: number
}

class FakeDb {
    private readonly syncState = new Map<string, SyncStateRow>()
    private readonly membersByLeaf = new Map<string, MemberRow>()
    private readonly elections = new Map<string, ElectionRow>()
    private seq = 0

    async query(text: string, values: unknown[] = []) {
        const result = this.runQuery(
            text,
            values,
            this.syncState,
            this.membersByLeaf,
            this.elections,
            this.seq
        )
        this.seq = result.nextSeq
        return result.queryResult
    }

    async connect() {
        let inTx = false
        let txSyncState = new Map<string, SyncStateRow>()
        let txMembersByLeaf = new Map<string, MemberRow>()
        let txElections = new Map<string, ElectionRow>()
        let txSeq = this.seq

        const client = {
            query: async (text: string, values: unknown[] = []) => {
                const sql = normalizeSql(text)

                if (sql === "begin") {
                    inTx = true
                    txSyncState = cloneMap(this.syncState)
                    txMembersByLeaf = cloneMap(this.membersByLeaf)
                    txElections = cloneMap(this.elections)
                    txSeq = this.seq
                    return { rowCount: null, rows: [] }
                }

                if (sql === "commit") {
                    if (!inTx) throw new Error("COMMIT without BEGIN")
                    this.replaceMaps(txSyncState, txMembersByLeaf, txElections, txSeq)
                    inTx = false
                    return { rowCount: null, rows: [] }
                }

                if (sql === "rollback") {
                    if (!inTx) throw new Error("ROLLBACK without BEGIN")
                    inTx = false
                    return { rowCount: null, rows: [] }
                }

                const targetSync = inTx ? txSyncState : this.syncState
                const targetMembers = inTx ? txMembersByLeaf : this.membersByLeaf
                const targetElections = inTx ? txElections : this.elections
                const result = this.runQuery(text, values, targetSync, targetMembers, targetElections, inTx ? txSeq : this.seq)
                if (inTx) {
                    txSeq = result.nextSeq
                } else {
                    this.seq = result.nextSeq
                }
                return result.queryResult
            },
            release: () => {
                // no-op for fake client
            }
        }

        return client
    }

    private replaceMaps(
        nextSyncState: Map<string, SyncStateRow>,
        nextMembersByLeaf: Map<string, MemberRow>,
        nextElections: Map<string, ElectionRow>,
        nextSeq: number
    ) {
        this.syncState.clear()
        for (const [k, v] of nextSyncState) this.syncState.set(k, { ...v })

        this.membersByLeaf.clear()
        for (const [k, v] of nextMembersByLeaf) this.membersByLeaf.set(k, { ...v })

        this.elections.clear()
        for (const [k, v] of nextElections) this.elections.set(k, { ...v })

        this.seq = nextSeq
    }

    private runQuery(
        text: string,
        values: unknown[],
        syncState: Map<string, SyncStateRow>,
        membersByLeaf: Map<string, MemberRow>,
        elections: Map<string, ElectionRow>,
        currentSeq: number = this.seq
    ) {
        const sql = normalizeSql(text)
        let nextSeq = currentSeq

        if (sql.startsWith("insert into elections")) {
            const election = String(values[0]).toLowerCase()
            const source = String(values[1]) as "seed" | "factory"
            const discoveredBlock = String(values[2])
            const discoveredLogIndex = String(values[3])

            if (elections.has(election)) {
                return { queryResult: { rowCount: 0, rows: [] }, nextSeq }
            }

            nextSeq += 1
            elections.set(election, {
                election_address: election,
                source,
                discovered_block: discoveredBlock,
                discovered_log_index: discoveredLogIndex,
                created_seq: nextSeq
            })
            return { queryResult: { rowCount: 1, rows: [{ election_address: election }] }, nextSeq }
        }

        if (sql.startsWith("select election_address from elections")) {
            const rows = [...elections.values()]
                .sort((a, b) => a.created_seq - b.created_seq)
                .map((row) => ({ election_address: row.election_address }))
            return { queryResult: { rowCount: rows.length, rows }, nextSeq }
        }

        if (sql.startsWith("insert into sync_state")) {
            const election = String(values[0]).toLowerCase()
            syncState.set(election, {
                last_processed_block: String(values[1]),
                last_processed_log_index: String(values[2]),
                last_processed_block_hash: (values[3] as string | null) ?? null
            })
            return { queryResult: { rowCount: 1, rows: [{ election_address: election }] }, nextSeq }
        }

        if (sql.startsWith("select last_processed_block, last_processed_log_index, last_processed_block_hash from sync_state")) {
            const election = String(values[0]).toLowerCase()
            const row = syncState.get(election)
            if (!row) return { queryResult: { rowCount: 0, rows: [] }, nextSeq }
            return { queryResult: { rowCount: 1, rows: [{ ...row }] }, nextSeq }
        }

        if (sql.startsWith("insert into members")) {
            const election = String(values[0]).toLowerCase()
            const groupId = String(values[1])
            const leafIndex = String(values[2])
            const commitment = String(values[3])
            const blockNumber = String(values[4])
            const logIndex = String(values[5])
            const leafKey = `${election}:${leafIndex}`
            const existingByLeaf = membersByLeaf.get(leafKey)

            if (existingByLeaf && existingByLeaf.identity_commitment !== commitment) {
                return { queryResult: { rowCount: 0, rows: [] }, nextSeq }
            }

            if (!existingByLeaf) {
                for (const row of membersByLeaf.values()) {
                    if (row.election_address === election && row.identity_commitment === commitment) {
                        throw new Error("duplicate key value violates unique constraint members_election_commitment")
                    }
                }
            }

            const nextRow: MemberRow = {
                election_address: election,
                group_id: groupId,
                leaf_index: leafIndex,
                identity_commitment: commitment,
                block_number: blockNumber,
                log_index: logIndex
            }
            membersByLeaf.set(leafKey, nextRow)
            return { queryResult: { rowCount: 1, rows: [{ election_address: election }] }, nextSeq }
        }

        if (sql.startsWith("select leaf_index, identity_commitment from members")) {
            const election = String(values[0]).toLowerCase()
            const rows = [...membersByLeaf.values()]
                .filter((row) => row.election_address === election)
                .sort((a, b) => {
                    const aLeaf = BigInt(a.leaf_index)
                    const bLeaf = BigInt(b.leaf_index)
                    if (aLeaf < bLeaf) return -1
                    if (aLeaf > bLeaf) return 1
                    return 0
                })
                .map((row) => ({
                    leaf_index: row.leaf_index,
                    identity_commitment: row.identity_commitment
                }))

            return { queryResult: { rowCount: rows.length, rows }, nextSeq }
        }

        throw new Error(`Unhandled SQL in fake DB: ${sql}`)
    }
}

function normalizeSql(sql: string) {
    return sql.replace(/\s+/g, " ").trim().toLowerCase()
}

function cloneMap<T extends object>(m: Map<string, T>) {
    return new Map([...m.entries()].map(([k, v]) => [k, { ...v }]))
}

async function withPatchedPool(run: () => Promise<void>) {
    const fakeDb = new FakeDb()
    const originalQuery = (pool as any).query
    const originalConnect = (pool as any).connect

    ;(pool as any).query = fakeDb.query.bind(fakeDb)
    ;(pool as any).connect = fakeDb.connect.bind(fakeDb)

    try {
        await run()
    } finally {
        ;(pool as any).query = originalQuery
        ;(pool as any).connect = originalConnect
    }
}

test("restart/bootstrap flow: seed -> bootstrap from DB -> serve proof -> restart -> continue indexing", async () => {
    const election = "0x00000000000000000000000000000000000000aa" as const
    const firstBlockHash = "0x0000000000000000000000000000000000000000000000000000000000000011" as const
    const secondBlockHash = "0x0000000000000000000000000000000000000000000000000000000000000012" as const

    await withPatchedPool(async () => {
        await seedElectionAddresses([election])
        assert.deepEqual(await loadElectionAddresses(), [election])

        await withTransaction(async (tx) => {
            await upsertMember(
                {
                    election,
                    groupId: "1",
                    leafIndex: 0n,
                    commitment: "111",
                    blockNumber: 10n,
                    logIndex: 0n
                },
                tx
            )
            await upsertMember(
                {
                    election,
                    groupId: "1",
                    leafIndex: 1n,
                    commitment: "222",
                    blockNumber: 11n,
                    logIndex: 0n
                },
                tx
            )
            await setSyncCursor(
                election,
                {
                    blockNumber: 11n,
                    logIndex: 0n,
                    blockHash: firstBlockHash
                },
                tx
            )
        })

        const bootstrapDeps = {
            getElectionMeta: async () => ({ groupId: 1n, depth: 20 })
        }

        const run1State = await bootstrapElectionState(election, bootstrapDeps)
        assert.equal(run1State.size, 2)

        const appRun1 = buildServer(
            new Map([[election, run1State]]),
            { getOnChainRoot: async () => run1State.getRoot() }
        )
        try {
            const res = await appRun1.inject({
                method: "GET",
                url: `/elections/${election}/proof?commitment=222`
            })
            assert.equal(res.statusCode, 200)
            const body = res.json()
            assert.equal(body.leaf, "222")
            assert.equal(body.root, run1State.getRoot().toString())
        } finally {
            await appRun1.close()
        }

        const run2State = await bootstrapElectionState(election, bootstrapDeps)
        assert.equal(run2State.size, 2)
        assert.equal(run2State.getRoot(), run1State.getRoot())

        const before = await getSyncCursor(election)
        assert.deepEqual(before, {
            blockNumber: 11n,
            logIndex: 0n,
            blockHash: firstBlockHash
        })
        assert.equal(computeNextFromCursor(before), 12n)

        const pending = buildPendingAdds(run2State, [
            {
                args: {
                    groupId: 1n,
                    index: 2n,
                    identityCommitment: 333n
                },
                blockNumber: 12n,
                logIndex: 0n
            }
        ])

        await persistBatch(run2State, pending, 12n, secondBlockHash)
        for (const add of pending) {
            run2State.addMember(add.commitment, add.leafIndex)
        }

        const after = await getSyncCursor(election)
        assert.deepEqual(after, {
            blockNumber: 12n,
            logIndex: 0n,
            blockHash: secondBlockHash
        })
        assert.deepEqual(
            (await loadMembers(election)).map((m) => [m.leaf_index, m.identity_commitment]),
            [
                ["0", "111"],
                ["1", "222"],
                ["2", "333"]
            ]
        )

        const appRun2 = buildServer(
            new Map([[election, run2State]]),
            { getOnChainRoot: async () => run2State.getRoot() }
        )
        try {
            const res = await appRun2.inject({
                method: "GET",
                url: `/elections/${election}/proof?commitment=333`
            })
            assert.equal(res.statusCode, 200)
            const body = res.json()
            assert.equal(body.leaf, "333")
            assert.equal(body.root, run2State.getRoot().toString())
        } finally {
            await appRun2.close()
        }
    })
})
