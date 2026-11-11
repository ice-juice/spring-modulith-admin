import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
const production = process.env.SMA_CACHE_E2E_PRODUCTION === '1'
const output = production ? 'test-results-cache-production' : 'test-results-cache'
export default defineConfig({
  testDir: './e2e', testMatch: 'cache-governance.spec.ts', workers: 1, fullyParallel: false, retries: 0,
  outputDir: output, reporter: [['list'], ['json', { outputFile: output + '/results.json' }]],
  use: { channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined), baseURL: 'http://127.0.0.1:5194', locale: 'zh-CN', timezoneId: 'Asia/Shanghai', screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: { command: 'npm run ' + (production ? 'preview' : 'dev') + ' -- --port 5194', url: 'http://127.0.0.1:5194', reuseExistingServer: false, timeout: 30000 },
})
