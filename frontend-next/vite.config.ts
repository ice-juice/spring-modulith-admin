import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { securityHeaders } from './security-headers.ts'
export default defineConfig({
  base: process.env.SMA_NEXT_BASE || '/',
  build: { manifest: true },
  preview: { headers: securityHeaders },
  plugins: [vue(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { headers: securityHeaders, host: '127.0.0.1', port: 5174, strictPort: true, watch: { ignored: ['**/test-results/**', '**/test-results-real*/**', '**/playwright-report/**', '**/.e2e-runtime/**'] }, proxy: {
    // Preserve the external Host and Origin for tenant resolution and WebSocket validation.
    '/api': { target: process.env.SMA_NEXT_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: false, ws: true, rewriteWsOrigin: false },
    '/actuator': { target: process.env.SMA_NEXT_BACKEND_URL || 'http://127.0.0.1:8080', changeOrigin: true },
  } },
})
