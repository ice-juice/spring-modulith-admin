import { defineConfig } from '@playwright/test'
import base from './playwright.cache.config'
export default defineConfig({ ...base, testMatch: ['operations.spec.ts', 'states.spec.ts'], outputDir: 'test-results-cache-regression', reporter: [['list'], ['json', { outputFile: 'test-results-cache-regression/results.json' }]] })
