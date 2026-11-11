import { defineConfig } from '@playwright/test'
import base from './playwright.config'
export default defineConfig({ ...base, testMatch: ['extension.spec.ts', 'table-workspace.spec.ts'], outputDir: 'test-results-extension',
  reporter: [['list'], ['json', { outputFile: 'test-results-extension/results.json' }]],
})
