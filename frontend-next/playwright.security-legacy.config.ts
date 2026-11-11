import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
export default defineConfig({
  testDir: './e2e-security', testMatch: 'protocol-compat.spec.ts', workers: 1, retries: 0,
  outputDir: 'test-results-security-legacy',
  use: { channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined), baseURL: 'http://127.0.0.1:5197', trace: 'retain-on-failure' },
  webServer: { command: 'npm --prefix ../frontend run dev -- --host 127.0.0.1 --port 5197 --strictPort', url: 'http://127.0.0.1:5197', reuseExistingServer: false, timeout: 30000 },
})
