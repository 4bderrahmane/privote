const INITIAL_RETRY_DELAY_MS = 1_000
const MAX_RETRY_DELAY_MS = 60_000

export function computeBootstrapRetryDelayMs(attempts: number): number {
    const safeAttempts = Math.max(1, attempts)
    const exp = INITIAL_RETRY_DELAY_MS * (2 ** (safeAttempts - 1))
    return Math.min(exp, MAX_RETRY_DELAY_MS)
}

type RetryEntry = {
    attempts: number
    nextRetryAtMs: number
}

export class BootstrapRetryQueue {
    private readonly entries = new Map<string, RetryEntry>()

    markFailure(address: string, nowMs: number = Date.now()) {
        const key = address.toLowerCase()
        const prev = this.entries.get(key)
        const attempts = (prev?.attempts ?? 0) + 1
        const delayMs = computeBootstrapRetryDelayMs(attempts)
        const nextRetryAtMs = nowMs + delayMs

        this.entries.set(key, { attempts, nextRetryAtMs })
        return { attempts, delayMs, nextRetryAtMs }
    }

    markSuccess(address: string) {
        this.entries.delete(address.toLowerCase())
    }

    dueAddresses(nowMs: number = Date.now()): string[] {
        return [...this.entries.entries()]
            .filter(([, entry]) => entry.nextRetryAtMs <= nowMs)
            .sort((a, b) => a[1].nextRetryAtMs - b[1].nextRetryAtMs)
            .map(([address]) => address)
    }

    has(address: string): boolean {
        return this.entries.has(address.toLowerCase())
    }
}
