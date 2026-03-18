import { loadElectionAddresses, migrate, seedElectionAddresses } from "./infrastructure/db.js"
import { env, electionAddresses, electionFactoryAddress } from "./config/env.js"
import { runFactoryElectionDiscoveryLoop, runIndexerLoop, bootstrapElectionState } from "./indexer"
import { buildServer } from "./app/server.js"
import type { ElectionGroupState } from "./domain/state.js"
import { BootstrapRetryQueue } from "./app/bootstrap-retry.js"

const BOOTSTRAP_RETRY_POLL_MS = 1_000

function sleep(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms))
}

async function main() {
    await migrate()

    const states = new Map<string, ElectionGroupState>()
    const starting = new Set<string>()
    const retryQueue = new BootstrapRetryQueue()

    const ensureIndexerRunning = async (address: `0x${string}`) => {
        const key = address.toLowerCase()
        if (states.has(key)) {
            retryQueue.markSuccess(key)
            return
        }
        if (starting.has(key)) return

        starting.add(key)
        try {
            const state = await bootstrapElectionState(address)
            states.set(key, state)
            retryQueue.markSuccess(key)

            void runIndexerLoop(state).catch((err) => {
                console.error(`[indexer] ${address} crashed`, err)
            })
        } catch (err) {
            const retry = retryQueue.markFailure(key)
            console.error(
                `[bootstrap] failed for ${address}; retrying in ${retry.delayMs}ms (attempt=${retry.attempts})`,
                err
            )
        } finally {
            starting.delete(key)
        }
    }

    const runBootstrapRetryLoop = async () => {
        for (;;) {
            const due = retryQueue.dueAddresses()
            for (const address of due) {
                await ensureIndexerRunning(address as `0x${string}`)
            }
            await sleep(BOOTSTRAP_RETRY_POLL_MS)
        }
    }

    await seedElectionAddresses(electionAddresses)

    const knownElectionAddresses = Array.from(
        new Set<`0x${string}`>([
            ...electionAddresses,
            ...(await loadElectionAddresses())
        ])
    )

    for (const addr of knownElectionAddresses) {
        await ensureIndexerRunning(addr)
    }

    void runBootstrapRetryLoop().catch((err) => {
        console.error("[bootstrap-retry] loop crashed", err)
    })

    if (electionFactoryAddress) {
        void runFactoryElectionDiscoveryLoop(electionFactoryAddress, {
            onElectionDiscovered: async (address) => {
                await ensureIndexerRunning(address)
            }
        }).catch((err) => {
            console.error(`[factory-indexer] ${electionFactoryAddress} crashed`, err)
        })
    } else {
        console.warn("[factory-indexer] FACTORY_ADDRESS not set; automatic election discovery disabled")
    }

    const app = buildServer(states)
    await app.listen({ port: env.PORT, host: "0.0.0.0" })
    app.log.info(`proof-service listening on ${env.PORT}`)
}

main().catch((err) => {
    console.error(err)
    process.exit(1)
})
