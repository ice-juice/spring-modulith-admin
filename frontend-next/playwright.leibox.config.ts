import { defineConfig } from '@playwright/test'
import base from './playwright.config'
const origin = 'http://127.0.0.1:5216'
process.env.SMA_E2E_MOCK_ORIGIN = origin
process.env.SMA_RULE_TEST_PORT = '5216'
const production = process.env.SMA_LEIBOX_STATIC === '1'
export default defineConfig({
  ...base, testMatch: /(?:leibox-ui|iam|rule-authoring|rule-flow|table-workspace|softlight-shell)\.spec\.ts/, outputDir: 'test-results-leibox',
  reporter: [['list'], ['json', { outputFile: 'test-results-leibox/results.json' }]],
  use: { ...base.use, baseURL: origin },
  webServer: { command: production ? 'npm run preview -- --port 5216' : 'npm run dev -- --port 5216', url: origin, reuseExistingServer: false, timeout: 60000 },
})
