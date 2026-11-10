import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
export default defineConfig({
  testDir: './e2e-real', workers: 1, fullyParallel: false, retries: 0, timeout: 90000,
  outputDir: 'test-results-real',
  reporter: [['list'], ['json', { outputFile: 'test-results-real/results.json' }]],
  use: { channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined), baseURL: 'http://127.0.0.1:5174', locale: 'zh-CN', timezoneId: 'Asia/Shanghai', trace: 'off', screenshot: 'only-on-failure' },
  webServer: { command: 'npm run dev', url: 'http://127.0.0.1:5174', reuseExistingServer: false, timeout: 30000 },
})
