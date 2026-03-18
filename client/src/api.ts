import axios from "axios";
import { getToken } from "./auth/keycloak.ts";

function resolveBaseUrl() {
    const configuredBase = import.meta.env.VITE_API_BASE_URL;
    if (configuredBase && configuredBase.trim().length > 0) {
        return configuredBase.endsWith("/api") ? configuredBase : `${configuredBase}/api`;
    }
    return "/api";
}

const api = axios.create({
    baseURL: resolveBaseUrl(),
});

api.interceptors.request.use(async (config) => {
    const token = await getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default api;
