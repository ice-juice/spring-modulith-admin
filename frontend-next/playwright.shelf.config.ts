import { defineConfig } from '@playwright/test'
import base from './playwright.config'
export default defineConfig({
  ...base, testMatch: /personal-shelf.spec.ts/, outputDir: 'test-results-shelf',
  reporter: [['list'], ['json', { outputFile: 'test-results-shelf/results.json' }]],
  use: { ...base.use, baseURL: 'http://127.0.0.1:5198' },
  webServer: { command: 'npm run dev -- --port 5198', url: 'http://127.0.0.1:5198', reuseExistingServer: false, timeout: 60000 },
})
