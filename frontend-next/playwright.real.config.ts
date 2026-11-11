import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
const port = Number(process.env.SMA_E2E_FRONTEND_PORT || 5174)
const legacyPort = Number(process.env.SMA_E2E_LEGACY_PORT || 5188)
const output = process.env.SMA_E2E_OUTPUT_DIR || (process.env.SMA_E2E_FRONTEND_PORT ? `test-results-real-${port}` : 'test-results-real')
const staticFrontend = process.env.SMA_E2E_STATIC === '1'
export default defineConfig({
  testDir: './e2e-real', workers: 1, fullyParallel: false, retries: 0, timeout: 90000,
  outputDir: output,
  reporter: [['list'], ['json', { outputFile: `${output}/results.json` }]],
  use: { channel: process.env.PLAYWRIGHT_CHANNEL || (existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe') ? 'chrome' : undefined), baseURL: `http://127.0.0.1:${port}`, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', trace: 'off', screenshot: 'only-on-failure' },
  webServer: [
    { command: `npm run ${staticFrontend ? 'preview' : 'dev'} -- --port ${port}`, url: `http://127.0.0.1:${port}`, reuseExistingServer: false, timeout: 30000 },
    ...(process.env.SMA_E2E_LEGACY === '1' ? [{ command: `npm run dev -- --host 127.0.0.1 --port ${legacyPort} --strictPort`, cwd: '../frontend', url: `http://127.0.0.1:${legacyPort}`, reuseExistingServer: false, timeout: 30000 }] : []),
  ],
})
