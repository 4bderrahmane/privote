import test from "node:test"
import assert from "node:assert/strict"
import { buildServer } from "../src/app/server"

const UNKNOWN_ELECTION = "0x0000000000000000000000000000000000000001"

test("GET /elections/:address/root rejects invalid addresses", async () => {
    const app = buildServer(new Map())

    try {
        const res = await app.inject({
            method: "GET",
            url: "/elections/not-an-address/root"
        })

        assert.equal(res.statusCode, 400)
        assert.match(res.body, /Invalid address/)
    } finally {
        await app.close()
    }
})

test("GET /elections/:address/root returns 404 for unknown election", async () => {
    const app = buildServer(new Map())

    try {
        const res = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/root`
        })

        assert.equal(res.statusCode, 404)
        assert.match(res.body, /Unknown election/)
    } finally {
        await app.close()
    }
})

test("GET /elections/:address/proof validates commitment", async () => {
    const app = buildServer(new Map())

    try {
        const missing = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/proof`
        })
        assert.equal(missing.statusCode, 400)
        assert.match(missing.body, /Invalid commitment/)

        const nonNumeric = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/proof?commitment=abc`
        })
        assert.equal(nonNumeric.statusCode, 400)
        assert.match(nonNumeric.body, /Invalid commitment/)
    } finally {
        await app.close()
    }
})

test("GET /health is not rate-limited", async () => {
    const app = buildServer(new Map())

    try {
        for (let i = 0; i < 60; i++) {
            const res = await app.inject({
                method: "GET",
                url: "/health"
            })

            assert.equal(res.statusCode, 200)
            assert.deepEqual(res.json(), { ok: true })
        }
    } finally {
        await app.close()
    }
})

test("GET /elections/:address/proof is rate-limited", async () => {
    const app = buildServer(new Map())
    const headers = { "x-forwarded-for": "10.0.0.10" }

    try {
        for (let i = 0; i < 29; i++) {
            const res = await app.inject({
                method: "GET",
                url: `/elections/${UNKNOWN_ELECTION}/proof?commitment=1`,
                headers
            })

            // Handler returns 404 for unknown election until limiter threshold is exceeded.
            assert.equal(res.statusCode, 404)
        }

        const lastAllowed = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/proof?commitment=1`,
            headers
        })
        assert.equal(lastAllowed.statusCode, 404)
        assert.equal(lastAllowed.headers["x-ratelimit-limit"], "30")
        assert.equal(lastAllowed.headers["x-ratelimit-remaining"], "0")

        const limited = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/proof?commitment=1`,
            headers
        })

        assert.equal(limited.statusCode, 429)
        assert.equal(limited.headers["x-ratelimit-limit"], "30")
        assert.equal(limited.headers["x-ratelimit-remaining"], "0")
        assert.ok(Number(limited.headers["retry-after"]) > 0)
    } finally {
        await app.close()
    }
})

test("GET /elections/:address/root is rate-limited", async () => {
    const app = buildServer(new Map())
    const headers = { "x-forwarded-for": "10.0.0.11" }

    try {
        for (let i = 0; i < 119; i++) {
            const res = await app.inject({
                method: "GET",
                url: `/elections/${UNKNOWN_ELECTION}/root`,
                headers
            })
            assert.equal(res.statusCode, 404)
        }

        const lastAllowed = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/root`,
            headers
        })
        assert.equal(lastAllowed.statusCode, 404)
        assert.equal(lastAllowed.headers["x-ratelimit-limit"], "120")
        assert.equal(lastAllowed.headers["x-ratelimit-remaining"], "0")

        const limited = await app.inject({
            method: "GET",
            url: `/elections/${UNKNOWN_ELECTION}/root`,
            headers
        })
        assert.equal(limited.statusCode, 429)
        assert.equal(limited.headers["x-ratelimit-limit"], "120")
        assert.equal(limited.headers["x-ratelimit-remaining"], "0")
    } finally {
        await app.close()
    }
})
