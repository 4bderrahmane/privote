# Privote

Privote is a privacy-preserving electronic voting platform that combines verified identity, anonymous participation, encrypted ballots, and blockchain-backed auditability.

The project is designed to solve a hard problem in digital elections: how to verify that only eligible voters participate, prevent double voting, and keep an auditable record of the process without exposing the voter's choice or linking the ballot back to the voter.

## Core Idea

The platform separates identity verification from ballot secrecy:

- Keycloak authenticates users and enforces access control.
- The client derives election-specific anonymous voter identities.
- Registered identity commitments are enrolled on-chain.
- The proof service rebuilds Merkle state and serves membership proofs.
- Votes are submitted as encrypted ballots together with a zero-knowledge proof.
- Smart contracts reject reused nullifiers, preventing double voting.
- The backend stores receipts and synchronizes local state with on-chain events.

## Why It Is Different

- Verified identity without exposing the vote itself.
- Anonymous, election-specific participation instead of reusable voting identities.
- Encrypted ballots whose plaintext choice is not published during voting.
- Zero-knowledge proof flow bound to the encrypted ballot payload.
- Nullifier-based one-person-one-vote enforcement.
- Tamper-evident lifecycle events anchored on-chain.

## End-to-End Flow

1. An administrator creates an election from the web client.
2. The client generates an election encryption keypair locally in the browser.
3. Only the election public key is sent to the backend and then to the election contract.
4. The election is deployed on-chain through the factory contract.
5. A voter authenticates with Keycloak.
6. The client derives an election-specific anonymous identity and registers its commitment.
7. The backend enrolls that commitment on-chain into the election group.
8. The proof service indexes group membership and serves Merkle proofs.
9. During voting, the client encrypts the ballot, generates a proof, and submits both.
10. The contract verifies the proof and rejects duplicate nullifiers.
11. The backend records the receipt, transaction hash, and ciphertext hash.
12. When voting ends, the election moves to tally state.

## Architecture

```text
+------------------+      +--------------------+
| React Client     | ---> | Keycloak           |
| - login          |      | - auth            |
| - key generation |      | - roles           |
| - proof creation |      +--------------------+
| - vote casting   |
+--------+---------+
         |
         v
+------------------+      +--------------------+
| Spring Boot API  | ---> | PostgreSQL         |
| - election rules |      | - elections       |
| - registrations  |      | - commitments     |
| - vote receipts  |      | - ballots         |
| - chain sync     |      +--------------------+
+--------+---------+
         |
         v
+------------------+      +--------------------+
| Smart Contracts  | <--> | Proof Service      |
| - ElectionFactory|      | - Merkle rebuild   |
| - Election       |      | - root checks      |
| - verifier       |      | - proof endpoint   |
+------------------+      +--------------------+
```

## Repository Layout

- `client/`: React + TypeScript + Vite frontend, Keycloak integration, local identity vault, browser-side election key management, proof generation, and voting UI.
- `server/`: Spring Boot backend, election lifecycle management, voter registration, vote submission, Keycloak-backed authorization, persistence, and chain event reconciliation.
- `hardhat/`: Solidity contracts, local deployment scripts, and contract tests.
- `proof-service/`: Fastify service that indexes on-chain group membership, rebuilds Merkle state, validates root consistency, and serves Merkle proofs to the client.

## Key Technologies

- Frontend: React, TypeScript, Vite, TanStack Query
- Identity and access control: Keycloak
- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA
- Database: PostgreSQL
- Blockchain integration: Solidity, Hardhat, Web3j, Ethers
- Privacy layer: Semaphore concepts, Groth16 verifier, zero-knowledge proof workflow
- Proof indexing service: Fastify, viem, Postgres

## What Is Already Implemented

- Keycloak-based authentication and role-aware access control
- Election creation with browser-generated election encryption keys
- On-chain election deployment through a factory contract
- Election lifecycle actions: deploy, start, end
- Candidate management for elections
- Election-specific voter commitment registration
- On-chain voter enrollment into the election group
- Proof-service indexing of group membership and Merkle proof serving
- Encrypted ballot submission from the client
- Zero-knowledge proof submission and contract-side verification path
- Nullifier-based duplicate vote prevention
- Backend event listeners for chain-to-database reconciliation
- Test coverage across backend services, smart contracts, and proof-service logic

## Current Project Status

This repository already demonstrates the core security architecture and the main voting workflow:

- secure authentication
- anonymous voter registration
- encrypted vote casting
- blockchain-backed verification
- duplicate-vote prevention

Some parts of the broader product experience are still evolving, especially result publication, final tally UX, and streamlined evaluator onboarding.

## Local Demo Setup

The project is split into four services/workspaces. A typical local demo needs:

- a local EVM node on `http://127.0.0.1:8545`
- Keycloak on `http://localhost:8080`
- the Spring Boot backend on `http://localhost:9090`
- the proof-service on `http://127.0.0.1:4010`
- the client on `http://localhost:5173`

### 1. Smart Contracts

From `hardhat/`:

```bash
npm install
npx hardhat node
```

In another terminal:

```bash
npm run deploy
```

Record the deployed `ElectionFactory` address and use it in the backend configuration.

### 2. Backend

From `server/`:

```bash
./mvnw spring-boot:run
```

The backend expects environment variables for:

- database connection
- Keycloak admin integration
- chain RPC URL
- relayer private key
- election factory address
- sync secret

See `server/src/main/resources/application.yaml` for the exact properties.

### 3. Proof Service

From `proof-service/`:

```bash
pnpm install
pnpm dev
```

The proof service requires:

- `RPC_URL`
- `DATABASE_URL`
- optional seeded election addresses or factory address

### 4. Keycloak

The repository includes a development compose file in `server/docker-compose.yaml` for Keycloak. The current setup is tailored to local development and may require path and database adjustments before reuse on another machine.

### 5. Frontend

From `client/`:

```bash
npm install
npm run dev
```

The client can be configured with:

- `VITE_API_BASE_URL`
- `VITE_KEYCLOAK_URL`
- `VITE_KEYCLOAK_REALM`
- `VITE_KEYCLOAK_CLIENT`
- `VITE_PROOF_SERVICE_BASE_URL`

## Example Workflow

One representative end-to-end flow is:

1. Login as admin
2. Create an election
3. Deploy the election on-chain
4. Add candidates
5. Start the election
6. Login as voter
7. Register the anonymous voting commitment
8. Show the commitment status, Merkle leaf index, and transaction hash
9. Cast an encrypted ballot
10. Show the ciphertext hash, nullifier, transaction hash, and block number
11. Attempt a duplicate vote and show that it is rejected

## Testing

Frontend:

```bash
cd client
npm test
```

Backend:

```bash
cd server
./mvnw test
```

Smart contracts:

```bash
cd hardhat
npm test
```

Proof service:

```bash
cd proof-service
pnpm test
pnpm build
```

## Security Notes

- Authentication is separated from vote casting.
- Voter identities are derived per election, reducing cross-election linkability.
- Ballots are encrypted before submission.
- Proof generation is tied to the encrypted ballot payload rather than plaintext choice.
- Nullifiers prevent duplicate voting without revealing voter identity.
- The backend stores verifiable receipts such as transaction hashes and ciphertext hashes.

## Next Steps

- Decentralize the tally with a 3-of-5 threshold decryption scheme so any 3 coordinators can combine their private key shares to decrypt and reveal the election private key
- Strengthen the existing tally and result publication dashboard into a production-grade workflow
- Add a portable environment template and one-command demo bootstrap for all services
