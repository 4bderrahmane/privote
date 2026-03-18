import test from "node:test"
import assert from "node:assert/strict"
import { BootstrapRetryQueue, computeBootstrapRetryDelayMs } from "../src/app/bootstrap-retry"

test("computeBootstrapRetryDelayMs uses exponential backoff with cap", () => {
    assert.equal(computeBootstrapRetryDelayMs(1), 1_000)
    assert.equal(computeBootstrapRetryDelayMs(2), 2_000)
    assert.equal(computeBootstrapRetryDelayMs(3), 4_000)
    assert.equal(computeBootstrapRetryDelayMs(10), 60_000)
})

test("markFailure schedules retries and increments attempts", () => {
    const queue = new BootstrapRetryQueue()

    const first = queue.markFailure("0xabc", 1_000)
    assert.equal(first.attempts, 1)
    assert.equal(first.delayMs, 1_000)
    assert.equal(first.nextRetryAtMs, 2_000)

    const second = queue.markFailure("0xAbC", 2_000)
    assert.equal(second.attempts, 2)
    assert.equal(second.delayMs, 2_000)
    assert.equal(second.nextRetryAtMs, 4_000)
})

test("dueAddresses returns only due addresses in due-time order", () => {
    const queue = new BootstrapRetryQueue()

    queue.markFailure("0xaaa", 0) // due at 1000
    queue.markFailure("0xbbb", 500) // due at 1500
    queue.markFailure("0xbbb", 1_500) // due at 3500 (attempt 2)

    assert.deepEqual(queue.dueAddresses(900), [])
    assert.deepEqual(queue.dueAddresses(1_000), ["0xaaa"])
    assert.deepEqual(queue.dueAddresses(3_500), ["0xaaa", "0xbbb"])
})

test("markSuccess removes a queued address", () => {
    const queue = new BootstrapRetryQueue()
    queue.markFailure("0xaaa", 0)
    assert.equal(queue.has("0xaaa"), true)

    queue.markSuccess("0xaaa")
    assert.equal(queue.has("0xaaa"), false)
    assert.deepEqual(queue.dueAddresses(10_000), [])
})
