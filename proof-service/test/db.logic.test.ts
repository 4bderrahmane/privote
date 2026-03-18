import test from "node:test"
import assert from "node:assert/strict"
import { getSyncCursor, loadMembers, pool, setSyncCursor, upsertMember, withTransaction } from "../src/infrastructure/db"

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

class FakeDb {
    private readonly syncState = new Map<string, SyncStateRow>()
    private readonly membersByLeaf = new Map<string, MemberRow>()

    async query(text: string, values: unknown[] = []) {
        return this.runQuery(text, values, this.syncState, this.membersByLeaf)
    }

    async connect() {
        let inTx = false
        let txSyncState = new Map<string, SyncStateRow>()
        let txMembersByLeaf = new Map<string, MemberRow>()

        const client = {
            query: async (text: string, values: unknown[] = []) => {
                const sql = normalizeSql(text)

                if (sql === "begin") {
                    inTx = true
                    txSyncState = cloneMap(this.syncState)
                    txMembersByLeaf = cloneMap(this.membersByLeaf)
                    return { rowCount: null, rows: [] }
                }

                if (sql === "commit") {
                    if (!inTx) throw new Error("COMMIT without BEGIN")
                    this.replaceMaps(txSyncState, txMembersByLeaf)
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
                return this.runQuery(text, values, targetSync, targetMembers)
            },
            release: () => {
                // no-op for fake client
            }
        }

        return client
    }

    private replaceMaps(
        nextSyncState: Map<string, SyncStateRow>,
        nextMembersByLeaf: Map<string, MemberRow>
    ) {
        this.syncState.clear()
        for (const [k, v] of nextSyncState) this.syncState.set(k, { ...v })

        this.membersByLeaf.clear()
        for (const [k, v] of nextMembersByLeaf) this.membersByLeaf.set(k, { ...v })
    }

    private runQuery(
        text: string,
        values: unknown[],
        syncState: Map<string, SyncStateRow>,
        membersByLeaf: Map<string, MemberRow>
    ) {
        const sql = normalizeSql(text)

        if (sql.startsWith("insert into sync_state")) {
            const election = String(values[0])
            syncState.set(election, {
                last_processed_block: String(values[1]),
                last_processed_log_index: String(values[2]),
                last_processed_block_hash: (values[3] as string | null) ?? null
            })
            return { rowCount: 1, rows: [{ election_address: election }] }
        }

        if (sql.startsWith("select last_processed_block, last_processed_log_index, last_processed_block_hash from sync_state")) {
            const election = String(values[0])
            const row = syncState.get(election)
            if (!row) return { rowCount: 0, rows: [] }
            return { rowCount: 1, rows: [{ ...row }] }
        }

        if (sql.startsWith("insert into members")) {
            const election = String(values[0])
            const groupId = String(values[1])
            const leafIndex = String(values[2])
            const commitment = String(values[3])
            const blockNumber = String(values[4])
            const logIndex = String(values[5])
            const leafKey = `${election}:${leafIndex}`
            const existingByLeaf = membersByLeaf.get(leafKey)

            if (existingByLeaf && existingByLeaf.identity_commitment !== commitment) {
                return { rowCount: 0, rows: [] }
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
            return { rowCount: 1, rows: [{ election_address: election }] }
        }

        if (sql.startsWith("select leaf_index, identity_commitment from members")) {
            const election = String(values[0])
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

            return { rowCount: rows.length, rows }
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

const ELECTION = "0x0000000000000000000000000000000000000001"

test("setSyncCursor inside withTransaction rolls back on transaction failure", async () => {
    await withPatchedPool(async () => {
        await assert.rejects(
            withTransaction(async (tx) => {
                await setSyncCursor(
                    ELECTION,
                    {
                        blockNumber: 15n,
                        logIndex: 4n,
                        blockHash: "0xabc"
                    },
                    tx
                )
                throw new Error("synthetic transaction failure")
            }),
            /synthetic transaction failure/
        )

        const cursor = await getSyncCursor(ELECTION)
        assert.deepEqual(cursor, { blockNumber: 0n, logIndex: -1n, blockHash: null })
    })
})

test("upsertMember throws conflict error on same leaf with different commitment", async () => {
    await withPatchedPool(async () => {
        await upsertMember({
            election: ELECTION,
            groupId: "1",
            leafIndex: 0n,
            commitment: "111",
            blockNumber: 10n,
            logIndex: 1n
        })

        await assert.rejects(
            upsertMember({
                election: ELECTION,
                groupId: "1",
                leafIndex: 0n,
                commitment: "222",
                blockNumber: 10n,
                logIndex: 2n
            }),
            /Member row conflict at leaf_index=0/
        )

        const members = await loadMembers(ELECTION)
        assert.deepEqual(members, [{ leaf_index: "0", identity_commitment: "111" }])
    })
})

test("loadMembers returns members ordered by leaf_index ascending", async () => {
    await withPatchedPool(async () => {
        await upsertMember({
            election: ELECTION,
            groupId: "1",
            leafIndex: 2n,
            commitment: "300",
            blockNumber: 11n,
            logIndex: 3n
        })
        await upsertMember({
            election: ELECTION,
            groupId: "1",
            leafIndex: 0n,
            commitment: "100",
            blockNumber: 11n,
            logIndex: 1n
        })
        await upsertMember({
            election: ELECTION,
            groupId: "1",
            leafIndex: 1n,
            commitment: "200",
            blockNumber: 11n,
            logIndex: 2n
        })

        const members = await loadMembers(ELECTION)
        assert.deepEqual(
            members.map((m) => [m.leaf_index, m.identity_commitment]),
            [
                ["0", "100"],
                ["1", "200"],
                ["2", "300"]
            ]
        )
    })
})
