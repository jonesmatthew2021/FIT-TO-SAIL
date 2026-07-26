import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/**
 * The dev server proxies `/api` to the backend on :8080 so the browser sees one origin. That
 * matters beyond convenience: the production deployment serves this bundle and the API from the
 * same origin, and the BFF session cookie (ADR 0003) is `SameSite` — so developing against a
 * cross-origin API would exercise a cookie policy that production never uses.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.CREWCOMP_API ?? 'http://127.0.0.1:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
