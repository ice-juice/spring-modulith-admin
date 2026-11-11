import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
export default defineConfig({
  testDir: './e2e-security', testMatch: ['browser-security.spec.ts', 'protocol-compat.spec.ts', 'submission.spec.ts'], workers: 1, retries: 0,
  outputDir: 'test-results-security',
  use: {
    channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined),
    baseURL: 'http://127.0.0.1:5196', screenshot: 'only-on-failure', trace: 'retain-on-failure',
  },
  webServer: { command: 'npm run dev -- --port 5196', url: 'http://127.0.0.1:5196', reuseExistingServer: false, timeout: 30000 },
})
