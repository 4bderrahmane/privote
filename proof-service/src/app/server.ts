import Fastify, {type FastifyInstance} from "fastify"
import rateLimit from "@fastify/rate-limit"
import {registerRoutes} from "./routes.js"
import type {ElectionGroupState} from "../domain/state.js"

export function buildServer(states: Map<string, ElectionGroupState>): FastifyInstance {
    const app = Fastify({
        logger: true,
        trustProxy: true
    })

    app.register(rateLimit, {
        global: false
    })

    registerRoutes(app, states)

    return app
}
