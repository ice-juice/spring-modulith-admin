import { defineConfig } from '@playwright/test'
import security from './playwright.security.config'
process.env.SMA_E2E_MOCK_ORIGIN = 'http://127.0.0.1:5196'
export default defineConfig({ ...security, testDir: './e2e', testMatch: 'extension.spec.ts',
  outputDir: 'test-results-security-extension', use: { ...security.use, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' } })
