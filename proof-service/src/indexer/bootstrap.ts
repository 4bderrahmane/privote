import { ElectionGroupState as ElectionGroupStateImpl } from "../domain/state"
import { electionContract } from "../domain/chain"
import { loadMembers } from "../infrastructure/db"

export type BootstrappedElection = {
    state: ElectionGroupStateImpl
    members: { leafIndex: number; commitment: bigint }[]
}

export type BootstrapDeps = {
    getElectionMeta: (electionAddress: `0x${string}`) => Promise<{
        groupId: bigint
        depth: number
    }>
    loadMembers: typeof loadMembers
}

const defaultBootstrapDeps: BootstrapDeps = {
    getElectionMeta: async (electionAddress) => {
        const c = electionContract(electionAddress)
        const groupId = await c.read.externalNullifier()
        const depth = Number(await c.read.getMerkleTreeDepth([groupId]))
        return { groupId, depth }
    },
    loadMembers
}

async function bootstrapElectionInternal(
    electionAddress: `0x${string}`,
    overrides: Partial<BootstrapDeps> = {}
): Promise<BootstrappedElection> {
    const deps: BootstrapDeps = {
        ...defaultBootstrapDeps,
        ...overrides
    }

    const { groupId, depth } = await deps.getElectionMeta(electionAddress)

    const state = new ElectionGroupStateImpl(electionAddress)
    state.init(groupId, depth)

    const rows = await deps.loadMembers(electionAddress)

    const members = rows.map((r: any) => ({
        leafIndex: Number(BigInt(r.leaf_index)),
        commitment: BigInt(r.identity_commitment)
    }))

    state.rebuildFromMembers(members)

    return { state, members }
}

export async function bootstrapElectionState(
    electionAddress: `0x${string}`,
    overrides: Partial<BootstrapDeps> = {}
): Promise<ElectionGroupStateImpl> {
    const { state } = await bootstrapElectionInternal(electionAddress, overrides)
    return state
}

export async function bootstrapElectionSnapshot(
    electionAddress: `0x${string}`,
    overrides: Partial<BootstrapDeps> = {}
): Promise<BootstrappedElection> {
    return bootstrapElectionInternal(electionAddress, overrides)
}
