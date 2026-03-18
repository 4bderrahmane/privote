import type {FastifyInstance} from "fastify"
import {z} from "zod"
import type {ElectionGroupState} from "../domain/state.js"
import {electionContract} from "../domain/chain.js"
import {getAddress, isAddress} from "viem"

const AddressSchema = z
    .string()
    .refine((v) => isAddress(v), "Invalid address")

// uint256 max is 78 digits; cap length to avoid BigInt DoS inputs
const CommitmentSchema = z
    .string()
    .regex(/^\d+$/)
    .max(78)

function normalizeAddress(address: string): string {
    // checksum-normalize then lowercase for map key stability
    return getAddress(address).toLowerCase()
}

export function registerRoutes(app: FastifyInstance, states: Map<string, ElectionGroupState>) {
    app.get("/health", async () => ({ok: true}))

    app.get<{ Params: { address: string } }>(
        "/elections/:address/root",
        {
            config: {
                rateLimit: {
                    max: 120,
                    timeWindow: "1 minute"
                }
            }
        },
        async (req, reply) => {
            const parsed = AddressSchema.safeParse(req.params.address)
            if (!parsed.success) return reply.code(400).send({error: parsed.error.issues[0]?.message ?? "Invalid address"})

            const address = normalizeAddress(parsed.data)
            const state = states.get(address)
            if (!state) return reply.code(404).send({error: "Unknown election"})

            const c = electionContract(state.election)
            const onChainRoot = await c.read.getMerkleTreeRoot([state.groupId])
            const offChainRoot = state.getRoot()

            return {
                groupId: state.groupId.toString(),
                expectedDepth: state.expectedDepth,
                onChainRoot: onChainRoot.toString(),
                offChainRoot: offChainRoot.toString(),
                match: onChainRoot === offChainRoot
            }
        }
    )

    app.get<{ Params: { address: string }, Querystring: { commitment?: string } }>(
        "/elections/:address/proof",
        {
            config: {
                rateLimit: {
                    max: 30,
                    timeWindow: "1 minute"
                }
            }
        },
        async (req, reply) => {
            const addrParsed = AddressSchema.safeParse(req.params.address)
            if (!addrParsed.success) return reply.code(400).send({error: addrParsed.error.issues[0]?.message ?? "Invalid address"})

            const commitmentParsed = CommitmentSchema.safeParse(req.query.commitment)
            if (!commitmentParsed.success) return reply.code(400).send({error: "Invalid commitment"})

            const address = normalizeAddress(addrParsed.data)
            const state = states.get(address)
            if (!state) return reply.code(404).send({error: "Unknown election"})

            let proof
            try {
                proof = state.getMerkleProofJSON(BigInt(commitmentParsed.data))
            } catch {
                // Don’t leak internal errors; treat as not found.
                return reply.code(404).send({error: "Commitment not found"})
            }

            const c = electionContract(state.election)
            const onChainRoot = await c.read.getMerkleTreeRoot([state.groupId])

            if (BigInt(proof.root) !== onChainRoot) {
                return reply.code(409).send({
                    error: "Indexer out of sync (root mismatch)",
                    expected: onChainRoot.toString(),
                    got: proof.root
                })
            }

            return {
                groupId: state.groupId.toString(),
                expectedDepth: state.expectedDepth,
                ...proof
            }
        }
    )
}
