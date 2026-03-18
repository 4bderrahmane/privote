import { loadElectionAddresses, migrate, seedElectionAddresses } from "./infrastructure/db.js"
import { env, electionAddresses, electionFactoryAddress } from "./config/env.js"
import { runFactoryElectionDiscoveryLoop, runIndexerLoop, bootstrapElectionState } from "./indexer"
import { buildServer } from "./app/server.js"
import type { ElectionGroupState } from "./domain/state.js"

async function main() {
    await migrate()

    const states = new Map<string, ElectionGroupState>()
    const starting = new Set<string>()

    const ensureIndexerRunning = async (address: `0x${string}`) => {
        const key = address.toLowerCase()
        if (states.has(key) || starting.has(key)) return

        starting.add(key)
        try {
            const state = await bootstrapElectionState(address)
            states.set(key, state)

            void runIndexerLoop(state).catch((err) => {
                console.error(`[indexer] ${address} crashed`, err)
            })
        } catch (err) {
            console.error(`[bootstrap] failed for ${address}`, err)
        } finally {
            starting.delete(key)
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
