import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
export default defineConfig({
  root: fileURLToPath(new URL('.', import.meta.url)),
  base: './',
  plugins: [vue(), tailwindcss()],
  resolve: { alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) } },
  server: { host: '127.0.0.1', port: 5186, strictPort: true, fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] } },
  build: { outDir: '../../docs/frontend-next/design-review-softlight/site', emptyOutDir: true },
})
