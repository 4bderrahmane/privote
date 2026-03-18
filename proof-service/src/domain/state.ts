import {Group, type MerkleProof} from "@semaphore-protocol/group"
import {Address} from "viem";

export type MerkleProofJSON = {
    index: number
    leaf: string
    root: string
    siblings: string[]
}

export function merkleProofToJSON(p: MerkleProof<bigint>): MerkleProofJSON {
    return {
        index: p.index,
        leaf: p.leaf.toString(),
        root: p.root.toString(),
        siblings: p.siblings.map((x) => x.toString())
    }
}

export function merkleProofFromJSON(mpj: MerkleProofJSON): MerkleProof<bigint> {
    return {
        index: mpj.index,
        leaf: BigInt(mpj.leaf),
        root: BigInt(mpj.root),
        siblings: mpj.siblings.map(BigInt)
    }
}

export class ElectionGroupState {
    public readonly election: Address
    public groupId!: bigint
    public expectedDepth!: number
    private group: Group = new Group()
    private readonly commitmentToIndex = new Map<string, number>()

    constructor(election: Address) {
        this.election = election
    }

    init(groupId: bigint, expectedDepth: number) {
        this.groupId = groupId
        this.expectedDepth = expectedDepth
        this.group = new Group()
        this.commitmentToIndex.clear()
    }

    getRoot(): bigint {
        return this.group.root
    }

    public get size(): number {
        return this.group.size
    }

    getMerkleProof(commitment: bigint): MerkleProof<bigint> {
        const idx = this.commitmentToIndex.get(commitment.toString())
        if (idx === undefined) throw new Error("Commitment not found in group")

        return this.group.generateMerkleProof(idx)
    }

    getMerkleProofJSON(commitment: bigint): MerkleProofJSON {
        return merkleProofToJSON(this.getMerkleProof(commitment))
    }


    /**
     *  members must be sorted by leafIndex ASC and contiguous (0 => n-1) for add-only groups.
     *  @param members
     */
    rebuildFromMembers(members: { leafIndex: number; commitment: bigint }[]) {
        this.assertContiguousMembers(members)

        const commitments = members.map((m) => m.commitment)
        this.group = new Group(commitments)

        this.commitmentToIndex.clear()
        for (const m of members) {
            this.commitmentToIndex.set(m.commitment.toString(), m.leafIndex)
        }
    }

    /**
     * Apply a MemberAdded event deterministically.
     * For add-only SemaphoreGroups usage, the leafIndex must equal the current size.
     * @param commitment
     * @param leafIndex
     */
    addMember(commitment: bigint, leafIndex: number) {
        const key = commitment.toString()

        if (this.commitmentToIndex.has(key)) return

        const expectedIndex = this.group.size
        if (leafIndex !== expectedIndex) {
            throw new Error(
                `Out-of-order member insert: got index=${leafIndex}, expected=${expectedIndex}`
            )
        }

        this.group.addMember(commitment)
        this.commitmentToIndex.set(key, leafIndex)
    }

    private assertContiguousMembers(members: { leafIndex: number; commitment: bigint }[]) {
        const seenCommitments = new Set<string>()

        for (let i = 0; i < members.length; i++) {
            const member = members[i]

            if (!Number.isSafeInteger(member.leafIndex) || member.leafIndex < 0) {
                throw new Error(`Invalid leafIndex at position=${i}: ${member.leafIndex}`)
            }

            if (member.leafIndex !== i) {
                throw new Error(
                    `Non-contiguous member snapshot at position=${i}: got leafIndex=${member.leafIndex}`
                )
            }

            const key = member.commitment.toString()
            if (seenCommitments.has(key)) {
                throw new Error(`Duplicate commitment in snapshot at leafIndex=${member.leafIndex}`)
            }

            seenCommitments.add(key)
        }
    }
}
