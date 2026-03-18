import 'dotenv/config'
import {z} from "zod"
import { getAddress } from "viem"

const postgresUrl = z
    .string()
    .min(1)
    .superRefine((value, ctx) => {
        let u: URL
        try {
            u = new URL(value)
        } catch {
            ctx.addIssue({
                code: "custom",
                message: "DATABASE_URL must be a valid URL (e.g. postgres://user:pass@host:5432/db)"
            })
            return
        }

        if (u.protocol !== "postgres:" && u.protocol !== "postgresql:") {
            ctx.addIssue({
                code: "custom",
                message: "DATABASE_URL must start with postgres:// or postgresql://"
            })
        }

        // `URL` stores credentials, but password may be empty.
        if (!u.username) {
            ctx.addIssue({
                code: "custom",
                message: "DATABASE_URL must include a username"
            })
        }
        if (!u.password) {
            ctx.addIssue({
                code: "custom",
                message:
                    "DATABASE_URL must include a password. If it contains special characters, URL-encode it (e.g. ':' -> %3A, '@' -> %40)."
            })
        }

        if (!u.hostname) {
            ctx.addIssue({
                code: "custom",
                message: "DATABASE_URL must include a host"
            })
        }
        if (!u.pathname || u.pathname === "/") {
            ctx.addIssue({
                code: "custom",
                message: "DATABASE_URL must include a database name (e.g. /proofdb)"
            })
        }
    })

const schema = z.object({
    RPC_URL: z.string().url(),
    DATABASE_URL: postgresUrl,
    ELECTION_ADDRESSES: z.string().min(1),
    PORT: z.coerce.number().default(4010),
    CONFIRMATIONS: z.coerce.number().default(5),
    LOG_BATCH_SIZE: z.coerce.number().default(50_000)
})

export const env = schema.parse(process.env)

export const electionAddresses = Array.from(
    new Set(
        env.ELECTION_ADDRESSES
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean)
            .map((s) => getAddress(s).toLowerCase() as `0x${string}`)
    )
)
