import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { host: '127.0.0.1', port: 5174, strictPort: true, watch: { ignored: ['**/test-results/**', '**/test-results-real/**', '**/playwright-report/**', '**/.e2e-runtime/**'] }, proxy: {
    // The loopback-only development proxy presents the same origin to Spring's
    // WebSocket handshake, matching the single-origin production deployment.
    '/api': { target: process.env.SMA_NEXT_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: true, ws: true, rewriteWsOrigin: true },
    '/actuator': { target: process.env.SMA_NEXT_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: true },
  } },
})
