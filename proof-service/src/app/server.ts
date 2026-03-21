import Fastify, {type FastifyInstance} from "fastify"
import cors from "@fastify/cors"
import rateLimit from "@fastify/rate-limit"
import {registerRoutes, type RouteDeps} from "./routes.js"
import type {ElectionGroupState} from "../domain/state.js"

export function buildServer(
    states: Map<string, ElectionGroupState>,
    routeDeps?: RouteDeps
): FastifyInstance {
    const app = Fastify({
        logger: true,
        trustProxy: true
    })

    app.register(async (instance) => {
        await instance.register(cors, {
            origin: true
        })

        await instance.register(rateLimit, {
            global: false
        })

        registerRoutes(instance, states, routeDeps)
    })

    return app
}
