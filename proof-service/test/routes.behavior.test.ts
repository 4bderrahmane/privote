import test from "node:test"
import assert from "node:assert/strict"
import { buildServer } from "../src/app/server"
import { ElectionGroupState } from "../src/domain/state"

const KNOWN_ELECTION = "0x0000000000000000000000000000000000000001" as const
const UNKNOWN_ELECTION = "0x0000000000000000000000000000000000000002" as const

function buildKnownState() {
    const state = new ElectionGroupState(KNOWN_ELECTION)
    state.init(1n, 20)
    state.addMember(111n, 0)
    state.addMember(222n, 1)
    return state
}

function buildKnownStatesMap(state: ElectionGroupState) {
    return new Map([[KNOWN_ELECTION.toLowerCase(), state]])
}

test("GET /elections/:address/proof rejects invalid address and invalid commitment input", async () => {
    const app = buildServer(new Map())

    try {
        const badAddress = await app.inject({
            method: "GET",
            url: "/elections/not-an-address/proof?commitment=111"
        })
        assert.equal(badAddress.statusCode, 400)
        assert.match(badAddress.body, /Invalid address/)

        const badCommitment = await app.inject({
            method: "GET",
            url: `/elections/${KNOWN_ELECTION}/proof?commitment=abc`
        })
        assert.equal(badCommitment.statusCode, 400)
        assert.match(badCommitment.body, /Invalid commitment/)
    } finally {
        await app.close()
    }
})

test("unknown election returns 404", async () => {
    const app = buildServer(new Map())

    try {
        const rootRes = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/root`
        })
        assert.equal(rootRes.statusCode, 404)
        assert.match(rootRes.body, /Unknown election/)

        const proofRes = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/proof?commitment=111`
        })
        assert.equal(proofRes.statusCode, 404)
        assert.match(proofRes.body, /Unknown election/)
    } finally {
        await app.close()
    }
})

test("unknown commitment returns 404", async () => {
    const state = buildKnownState()
    let onChainRootCalls = 0
    const app = buildServer(buildKnownStatesMap(state), {
        getOnChainRoot: async () => {
            onChainRootCalls++
            return state.getRoot()
        }
    })

    try {
        const res = await app.inject({
            method: "GET",
            url: `/elections/${KNOWN_ELECTION}/proof?commitment=999`
        })
        assert.equal(res.statusCode, 404)
        assert.match(res.body, /Commitment not found/)
        assert.equal(onChainRootCalls, 0)
    } finally {
        await app.close()
    }
})

test("root mismatch returns 409", async () => {
    const state = buildKnownState()
    const offChainRoot = state.getRoot()
    const onChainRoot = offChainRoot + 1n
    const app = buildServer(buildKnownStatesMap(state), {
        getOnChainRoot: async () => onChainRoot
    })

    try {
        const res = await app.inject({
            method: "GET",
            url: `/elections/${KNOWN_ELECTION}/proof?commitment=111`
        })
        assert.equal(res.statusCode, 409)

        const body = res.json()
        assert.equal(body.error, "Indexer out of sync (root mismatch)")
        assert.equal(body.expected, onChainRoot.toString())
        assert.equal(body.got, offChainRoot.toString())
    } finally {
        await app.close()
    }
})

test("happy path returns a proof payload consumable by clients", async () => {
    const state = buildKnownState()
    const app = buildServer(buildKnownStatesMap(state), {
        getOnChainRoot: async () => state.getRoot()
    })

    try {
        const res = await app.inject({
            method: "GET",
            url: `/elections/${KNOWN_ELECTION}/proof?commitment=222`
        })
        assert.equal(res.statusCode, 200)

        const body = res.json()
        assert.equal(body.groupId, "1")
        assert.equal(body.expectedDepth, 20)
        assert.equal(body.leaf, "222")
        assert.equal(typeof body.index, "number")
        assert.equal(body.root, state.getRoot().toString())
        assert.ok(Array.isArray(body.siblings))

        BigInt(body.root)
        for (const sibling of body.siblings as string[]) {
            assert.equal(typeof sibling, "string")
            BigInt(sibling)
        }
    } finally {
        await app.close()
    }
})

test("root endpoint returns 503 when RPC is unavailable", async () => {
    const state = buildKnownState()
    const app = buildServer(buildKnownStatesMap(state), {
        getOnChainRoot: async () => {
            throw new Error("provider timeout details should not leak")
        }
    })

    try {
        const res = await app.inject({
            method: "GET",
            url: `/elections/${KNOWN_ELECTION}/root`
        })
        assert.equal(res.statusCode, 503)

        const body = res.json()
        assert.deepEqual(body, { error: "RPC unavailable" })
        assert.equal(res.body.includes("provider timeout"), false)
    } finally {
        await app.close()
    }
})

test("proof endpoint returns 503 when RPC is unavailable", async () => {
    const state = buildKnownState()
    const app = buildServer(buildKnownStatesMap(state), {
        getOnChainRoot: async () => {
            throw new Error("provider timeout details should not leak")
        }
    })

    try {
        const res = await app.inject({
            method: "GET",
            url: `/elections/${KNOWN_ELECTION}/proof?commitment=111`
        })
        assert.equal(res.statusCode, 503)

        const body = res.json()
        assert.deepEqual(body, { error: "RPC unavailable" })
        assert.equal(res.body.includes("provider timeout"), false)
    } finally {
        await app.close()
    }
})
