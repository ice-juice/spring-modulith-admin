import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
export default defineConfig({
  testDir: './e2e-security', testMatch: 'production-security.spec.ts', workers: 1, retries: 0,
  outputDir: 'test-results-security-production',
  use: { channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined), baseURL: 'http://127.0.0.1:4196', trace: 'retain-on-failure' },
  webServer: { command: 'npm run preview -- --host 127.0.0.1 --port 4196 --strictPort', url: 'http://127.0.0.1:4196', reuseExistingServer: false, timeout: 30000 },
})
