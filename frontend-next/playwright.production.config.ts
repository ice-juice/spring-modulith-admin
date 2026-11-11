import { defineConfig } from '@playwright/test'
import base from './playwright.config'

// Verify built assets through the same mock contracts; never reuse a dev server.
export default defineConfig({
  ...base,
  testMatch: ['routes.spec.ts', 'softlight-shell.spec.ts', 'softlight-pages.spec.ts', 'display-polish.spec.ts'],
  outputDir: 'test-results-production',
  reporter: [['list'], ['json', { outputFile: 'test-results-production/results.json' }]],
  webServer: {
    command: 'node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 5174 --strictPort',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: false,
    timeout: 30000,
  },
})
