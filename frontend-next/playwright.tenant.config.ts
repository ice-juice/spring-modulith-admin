import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
const backend = 'http://127.0.0.1:5199'
export default defineConfig({
  testDir: './e2e-tenant', workers: 1, retries: 0, timeout: 30000,
  outputDir: 'test-results-tenant', reporter: [['list'], ['json', { outputFile: 'test-results-tenant/results.json' }]],
  use: { channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined), trace: 'retain-on-failure', screenshot: 'only-on-failure', locale: 'zh-CN' },
  projects: [
    { name: 'next', use: { baseURL: 'http://127.0.0.1:5197' } },
    { name: 'legacy', use: { baseURL: 'http://127.0.0.1:5198' } },
  ],
  webServer: [
    { command: 'node e2e-tenant/proxy-backend.mjs', url: backend + '/health', reuseExistingServer: false },
    { command: 'npx vite --host 127.0.0.1 --port 5197 --strictPort', url: 'http://127.0.0.1:5197', env: { SMA_NEXT_BACKEND_URL: backend }, reuseExistingServer: false },
    { command: 'npx vite --host 127.0.0.1 --port 5198 --strictPort', cwd: fileURLToPath(new URL('../frontend', import.meta.url)), url: 'http://127.0.0.1:5198', env: { SMA_NEXT_BACKEND_URL: backend }, reuseExistingServer: false },
  ],
})
