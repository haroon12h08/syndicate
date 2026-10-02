import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin in development too, so the app never needs an API host or CORS.
    proxy: {
      '/api': {
        target: process.env.SYNDICATE_API_ORIGIN || 'http://localhost:8080',
        changeOrigin: true,
        // The browser labels these requests with the dev server's origin, which the API would
        // see as cross-origin only because the ports differ in development. Dropping the header
        // makes the proxied request what it really is - same origin - so no CORS setting is
        // needed here or in production.
        configure: (proxy) => proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
      },
    },
  },
})
