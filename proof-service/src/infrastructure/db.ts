import pg from "pg"
import {env} from "../config/env"

export const pool = new pg.Pool({
    connectionString: env.DATABASE_URL
})

export async function migrate() {
    await pool.query(`
        CREATE TABLE IF NOT EXISTS sync_state
        (
            election_address          TEXT PRIMARY KEY,
            last_processed_block      BIGINT NOT NULL DEFAULT 0,
            last_processed_log_index  BIGINT NOT NULL DEFAULT -1,
            last_processed_block_hash TEXT
        );

        ALTER TABLE sync_state
            ADD COLUMN IF NOT EXISTS last_processed_log_index BIGINT NOT NULL DEFAULT -1;

        ALTER TABLE sync_state
            ADD COLUMN IF NOT EXISTS last_processed_block_hash TEXT;

        CREATE TABLE IF NOT EXISTS members
        (
            election_address    TEXT   NOT NULL,
            group_id            TEXT   NOT NULL,
            leaf_index          BIGINT NOT NULL,
            identity_commitment TEXT   NOT NULL,
            block_number        BIGINT NOT NULL,
            log_index           BIGINT NOT NULL,
            PRIMARY KEY (election_address, leaf_index),
            UNIQUE (election_address, identity_commitment)
        );

        --         CREATE INDEX IF NOT EXISTS members_commitment_idx
        --         ON members (election_address, identity_commitment);

        CREATE INDEX IF NOT EXISTS members_by_block
            ON members (election_address, block_number, log_index);

        CREATE TABLE IF NOT EXISTS elections
        (
            election_address TEXT PRIMARY KEY,
            source           TEXT      NOT NULL,
            discovered_block BIGINT    NOT NULL DEFAULT 0,
            discovered_log_index BIGINT NOT NULL DEFAULT -1,
            created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS factory_sync_state
        (
            factory_address          TEXT PRIMARY KEY,
            last_processed_block     BIGINT NOT NULL DEFAULT 0,
            last_processed_log_index BIGINT NOT NULL DEFAULT -1,
            last_processed_block_hash TEXT
        );

        ALTER TABLE factory_sync_state
            ADD COLUMN IF NOT EXISTS last_processed_block_hash TEXT;
    `)
}

export type SyncCursor = {
    blockNumber: bigint
    logIndex: bigint
    blockHash: string | null
}

export async function getSyncCursor(election: string): Promise<SyncCursor> {
    const r = await pool.query(
        `SELECT last_processed_block, last_processed_log_index, last_processed_block_hash
         FROM sync_state
         WHERE election_address = $1`,
        [election]
    )
    if (r.rowCount === 0) return {blockNumber: 0n, logIndex: -1n, blockHash: null}
    return {
        blockNumber: BigInt(r.rows[0].last_processed_block),
        logIndex: BigInt(r.rows[0].last_processed_log_index),
        blockHash: (r.rows[0].last_processed_block_hash as string | null) ?? null
    }
}

export type FactorySyncCursor = {
    blockNumber: bigint
    logIndex: bigint
    blockHash: string | null
}

export async function getFactorySyncCursor(
    factory: string,
    startBlock: bigint = 0n
): Promise<FactorySyncCursor> {
    const r = await pool.query(
        `SELECT last_processed_block, last_processed_log_index, last_processed_block_hash
         FROM factory_sync_state
         WHERE factory_address = $1`,
        [factory]
    )

    if (r.rowCount === 0) {
        if (startBlock === 0n) return { blockNumber: 0n, logIndex: -1n, blockHash: null }
        return { blockNumber: startBlock - 1n, logIndex: -1n, blockHash: null }
    }

    return {
        blockNumber: BigInt(r.rows[0].last_processed_block),
        logIndex: BigInt(r.rows[0].last_processed_log_index),
        blockHash: (r.rows[0].last_processed_block_hash as string | null) ?? null
    }
}

export async function setFactorySyncCursor(
    factory: string,
    cursor: FactorySyncCursor,
    client: pg.PoolClient | pg.Pool = pool
) {
    await client.query(
        `
            INSERT INTO factory_sync_state (factory_address, last_processed_block, last_processed_log_index, last_processed_block_hash)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (factory_address)
                DO UPDATE SET last_processed_block     = EXCLUDED.last_processed_block,
                              last_processed_log_index = EXCLUDED.last_processed_log_index,
                              last_processed_block_hash = EXCLUDED.last_processed_block_hash
        `,
        [factory, cursor.blockNumber.toString(), cursor.logIndex.toString(), cursor.blockHash]
    )
}

export async function setSyncCursor(
    election: string,
    cursor: SyncCursor,
    client: pg.PoolClient | pg.Pool = pool
) {
    await client.query(
        `
            INSERT INTO sync_state (election_address, last_processed_block, last_processed_log_index, last_processed_block_hash)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (election_address)
                DO UPDATE SET last_processed_block      = EXCLUDED.last_processed_block,
                              last_processed_log_index  = EXCLUDED.last_processed_log_index,
                              last_processed_block_hash = EXCLUDED.last_processed_block_hash
        `,
        [election, cursor.blockNumber.toString(), cursor.logIndex.toString(), cursor.blockHash]
    )
}

export async function withTransaction<T>(fn: (client: pg.PoolClient) => Promise<T>): Promise<T> {
    const c = await pool.connect()
    try {
        await c.query("BEGIN")
        const res = await fn(c)
        await c.query("COMMIT")
        return res
    } catch (e) {
        await c.query("ROLLBACK")
        throw e
    } finally {
        c.release()
    }
}

export type MemberRow = {
    leaf_index: string
    identity_commitment: string
}

export async function loadMembers(election: string): Promise<MemberRow[]> {
    const r = await pool.query(
        `SELECT leaf_index, identity_commitment
         FROM members
         WHERE election_address = $1
         ORDER BY leaf_index`,
        [election]
    )
    return r.rows
}

export async function upsertElectionAddress(
    election: string,
    discovered: { source: "seed" | "factory"; blockNumber?: bigint; logIndex?: bigint } = { source: "factory" },
    client: pg.PoolClient | pg.Pool = pool
): Promise<boolean> {
    const r = await client.query(
        `
            INSERT INTO elections (election_address, source, discovered_block, discovered_log_index)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (election_address)
                DO NOTHING
            RETURNING election_address
        `,
        [
            election,
            discovered.source,
            (discovered.blockNumber ?? 0n).toString(),
            (discovered.logIndex ?? -1n).toString()
        ]
    )

    return (r.rowCount ?? 0) > 0
}

export async function seedElectionAddresses(elections: readonly string[]) {
    if (elections.length === 0) return

    await withTransaction(async (tx) => {
        for (const election of elections) {
            await upsertElectionAddress(election, { source: "seed" }, tx)
        }
    })
}

export async function loadElectionAddresses(): Promise<`0x${string}`[]> {
    const r = await pool.query(
        `SELECT election_address
         FROM elections
         ORDER BY created_at ASC`
    )

    return r.rows.map((row) => String(row.election_address).toLowerCase() as `0x${string}`)
}

export async function deleteFactoryDiscoveredElectionsFromBlock(
    fromBlock: bigint,
    client: pg.PoolClient | pg.Pool = pool
) {
    await client.query(
        `DELETE
         FROM elections
         WHERE source = 'factory'
           AND discovered_block >= $1`,
        [fromBlock.toString()]
    )
}

export async function upsertMember(params: {
    election: string
    groupId: string
    leafIndex: bigint
    commitment: string
    blockNumber: bigint
    logIndex: bigint
}, client: pg.PoolClient | pg.Pool = pool) {
    const r = await client.query(
        `
            INSERT INTO members (election_address, group_id, leaf_index, identity_commitment, block_number, log_index)
            VALUES ($1, $2, $3, $4, $5, $6)
            ON CONFLICT (election_address, leaf_index)
                DO UPDATE SET group_id            = EXCLUDED.group_id,
                              identity_commitment = EXCLUDED.identity_commitment,
                              block_number        = EXCLUDED.block_number,
                              log_index           = EXCLUDED.log_index
            WHERE members.identity_commitment = EXCLUDED.identity_commitment
            RETURNING election_address
        `,
        [
            params.election,
            params.groupId,
            params.leafIndex.toString(),
            params.commitment,
            params.blockNumber.toString(),
            params.logIndex.toString()
        ]
    )

    if (r.rowCount === 0) {
        throw new Error(
            `Member row conflict at leaf_index=${params.leafIndex.toString()} for election=${params.election}: commitment mismatch`
        )
    }
}
