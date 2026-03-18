import test from "node:test"
import assert from "node:assert/strict"
import { ElectionGroupState } from "../src/domain/state"

const ELECTION = "0x0000000000000000000000000000000000000001" as const

function freshState() {
    const state = new ElectionGroupState(ELECTION)
    state.init(1n, 20)
    return state
}

test("rebuildFromMembers accepts contiguous unique snapshots", () => {
    const state = freshState()

    state.rebuildFromMembers([
        { leafIndex: 0, commitment: 111n },
        { leafIndex: 1, commitment: 222n }
    ])

    assert.equal(state.size, 2)
    assert.equal(state.getMerkleProof(222n).index, 1)
})

test("rebuildFromMembers fails fast when snapshot has a missing leaf index", () => {
    const state = freshState()

    assert.throws(
        () =>
            state.rebuildFromMembers([
                { leafIndex: 0, commitment: 111n },
                { leafIndex: 2, commitment: 222n }
            ]),
        /Non-contiguous member snapshot/
    )
})

test("rebuildFromMembers fails fast on duplicate commitments", () => {
    const state = freshState()

    assert.throws(
        () =>
            state.rebuildFromMembers([
                { leafIndex: 0, commitment: 777n },
                { leafIndex: 1, commitment: 777n }
            ]),
        /Duplicate commitment/
    )
})

test("rebuildFromMembers fails fast on invalid leafIndex", () => {
    const state = freshState()

    assert.throws(
        () =>
            state.rebuildFromMembers([
                { leafIndex: -1, commitment: 555n }
            ]),
        /Invalid leafIndex/
    )
})

test("rebuildFromMembers fails fast on unsafe leafIndex", () => {
    const state = freshState()

    assert.throws(
        () =>
            state.rebuildFromMembers([
                { leafIndex: Number.MAX_SAFE_INTEGER + 1, commitment: 555n }
            ]),
        /Invalid leafIndex/
    )
})

test("addMember rejects non-sequential inserts", () => {
    const state = freshState()
    state.addMember(111n, 0)

    assert.throws(
        () => state.addMember(222n, 2),
        /Out-of-order member insert/
    )

    assert.equal(state.size, 1)
})

test("getMerkleProof fails cleanly for unknown commitments", () => {
    const state = freshState()
    state.addMember(111n, 0)

    assert.throws(
        () => state.getMerkleProof(999n),
        /Commitment not found in group/
    )
})
