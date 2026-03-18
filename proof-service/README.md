# Proof Service

Proof-service indexes election `MemberAdded` events, rebuilds/maintains Merkle state, and serves roots/proofs to the client.

## Current Status

Implemented and verified:

- Transaction-safe cursor + member persistence.
- Correct startup/restart semantics (including block `0`).
- Reorg handling for both election indexer and factory discovery.
- Snapshot validation when rebuilding state from DB.
- Factory-based automatic election discovery.
- Bootstrap retry queue for transient startup failures.
- Explicit `503` responses when RPC reads fail.
- Rate limiting on proof/root routes.
- Integration-style restart/bootstrap test coverage.
- Dependency cleanup and dead-code removal.

## Quick Start

1. Install dependencies:

```bash
pnpm install
```

2. Configure environment in `.env` (or export vars directly).

3. Start in dev mode:

```bash
pnpm dev
```

4. Production mode:

```bash
pnpm build
pnpm start
```

## Environment Variables

- `RPC_URL` (required): chain RPC URL.
- `DATABASE_URL` (required): Postgres URL.
- `ELECTION_ADDRESSES` (optional): comma-separated seed election addresses.
- `FACTORY_ADDRESS` (optional): ElectionFactory address for auto-discovery.
- `FACTORY_START_BLOCK` (optional, default `0`): starting block for factory scan.
- `PORT` (optional, default `4010`): HTTP port.
- `CONFIRMATIONS` (optional, default `5`): confirmation window before indexing.
- `LOG_BATCH_SIZE` (optional, default `50000`): block batch size for log scans.

## API

- `GET /health`
  - `200`: `{ "ok": true }`

- `GET /elections/:address/root`
  - `200`: on-chain + off-chain root and `match` boolean
  - `404`: unknown election
  - `503`: RPC unavailable

- `GET /elections/:address/proof?commitment=<uint256>`
  - `200`: proof payload (`root`, `leaf`, `siblings`, etc.)
  - `400`: invalid address/commitment
  - `404`: unknown election or missing commitment
  - `409`: root mismatch (indexer out of sync)
  - `503`: RPC unavailable

## Validation

Internal checks:

```bash
pnpm test
pnpm build
```

Live environment smoke validation:

```bash
PROOF_BASE_URL=http://127.0.0.1:4010 \
VALIDATE_ELECTION_ADDRESS=0x... \
VALIDATE_COMMITMENT=123... \
pnpm validate:live
```

`validate:live` checks:

1. `/health` responds.
2. `/root` returns `match: true`.
3. `/proof` returns a proof for the commitment.
4. proof root equals both on-chain and off-chain roots.
