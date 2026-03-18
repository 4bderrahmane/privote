import test from "node:test"
import assert from "node:assert/strict"
import { buildServer } from "../src/app/server"

test("GET /health returns ok=true", async () => {
    const app = buildServer(new Map())

    try {
        const res = await app.inject({
            method: "GET",
            url: "/health"
        })

        assert.equal(res.statusCode, 200)
        assert.deepEqual(res.json(), { ok: true })
    } finally {
        await app.close()
    }
})
