type ValidationConfig = {
    baseUrl: string
    electionAddress: string
    commitment: string
}

function requireEnv(name: string): string {
    const value = process.env[name]?.trim()
    if (!value) {
        throw new Error(`Missing required env var: ${name}`)
    }
    return value
}

function readConfig(): ValidationConfig {
    return {
        baseUrl: requireEnv("PROOF_BASE_URL").replace(/\/+$/, ""),
        electionAddress: requireEnv("VALIDATE_ELECTION_ADDRESS"),
        commitment: requireEnv("VALIDATE_COMMITMENT")
    }
}

async function expectJson(url: string) {
    const res = await fetch(url, {
        headers: {
            accept: "application/json"
        }
    })

    let body: any
    try {
        body = await res.json()
    } catch {
        const text = await res.text()
        throw new Error(`Expected JSON from ${url}, got: ${text}`)
    }

    return { res, body }
}

async function main() {
    const cfg = readConfig()

    const health = await fetch(`${cfg.baseUrl}/health`)
    if (!health.ok) {
        throw new Error(`Health check failed: HTTP ${health.status}`)
    }

    const rootUrl = `${cfg.baseUrl}/elections/${cfg.electionAddress}/root`
    const { res: rootRes, body: root } = await expectJson(rootUrl)
    if (rootRes.status !== 200) {
        throw new Error(`Root endpoint failed: HTTP ${rootRes.status} body=${JSON.stringify(root)}`)
    }
    if (root.match !== true) {
        throw new Error(`Indexer root mismatch: ${JSON.stringify(root)}`)
    }

    const proofUrl = `${cfg.baseUrl}/elections/${cfg.electionAddress}/proof?commitment=${cfg.commitment}`
    const { res: proofRes, body: proof } = await expectJson(proofUrl)
    if (proofRes.status !== 200) {
        throw new Error(`Proof endpoint failed: HTTP ${proofRes.status} body=${JSON.stringify(proof)}`)
    }

    if (typeof proof.root !== "string") {
        throw new Error(`Proof payload missing root: ${JSON.stringify(proof)}`)
    }
    if (proof.root !== root.offChainRoot || proof.root !== root.onChainRoot) {
        throw new Error(
            `Proof root mismatch (proof=${proof.root}, offChain=${root.offChainRoot}, onChain=${root.onChainRoot})`
        )
    }
    if (proof.leaf !== cfg.commitment) {
        throw new Error(`Proof leaf mismatch (expected=${cfg.commitment}, got=${proof.leaf})`)
    }

    console.log("Live validation passed.")
    console.log(
        JSON.stringify(
            {
                election: cfg.electionAddress,
                commitment: cfg.commitment,
                root: proof.root
            },
            null,
            2
        )
    )
}

main().catch((err) => {
    console.error(`[validate-live] ${err instanceof Error ? err.message : String(err)}`)
    process.exit(1)
})
