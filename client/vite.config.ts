import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:9090",
        changeOrigin: true,
        secure: false,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@components': path.resolve(__dirname, "src/shared/components"),
      '@features': path.resolve(__dirname, "src/shared/features"),
      '@pages': path.resolve(__dirname, "src/shared/pages"),
      '@shared-types': path.resolve(__dirname, "src/shared/types"),
      '@services': path.resolve(__dirname, "src/shared/services"),
    },
  },
});
