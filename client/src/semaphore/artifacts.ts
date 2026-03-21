import type { SnarkArtifacts } from "@zk-kit/artifacts";

export const SEMAPHORE_ARTIFACT_DEPTH = 20;

const PUBLIC_ZK_DIR = "zk";

function artifactFileName(extension: "wasm" | "zkey"): string {
    return `semaphore_${SEMAPHORE_ARTIFACT_DEPTH}.${extension}`;
}

function browserArtifactPath(extension: "wasm" | "zkey"): string {
    return `/${PUBLIC_ZK_DIR}/${artifactFileName(extension)}`;
}

function nodeArtifactPath(extension: "wasm" | "zkey"): string {
    const relativePath = ["..", "..", "public", PUBLIC_ZK_DIR, artifactFileName(extension)].join("/");
    return decodeURIComponent(new URL(relativePath, import.meta.url).pathname);
}

export function getSemaphoreSnarkArtifacts(): SnarkArtifacts {
    const isBrowser = globalThis.window !== undefined && typeof document !== "undefined";

    return {
        wasm: isBrowser ? browserArtifactPath("wasm") : nodeArtifactPath("wasm"),
        zkey: isBrowser ? browserArtifactPath("zkey") : nodeArtifactPath("zkey"),
    };
}
