import { globalIgnores } from 'eslint/config'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'
import skipFormatting from 'eslint-config-prettier/flat'
import vueI18n from '@intlify/eslint-plugin-vue-i18n'
export default defineConfigWithVueTs(
  globalIgnores(['dist/**', 'coverage/**', 'test-results/**', 'playwright-report/**']),
  ...pluginVue.configs['flat/essential'], vueTsConfigs.recommended, skipFormatting,
  { files: ['src/components/ui/**/*.vue'], rules: { 'vue/multi-word-component-names': 'off' } },
  { files: ['src/**/*.vue'], plugins: { '@intlify/vue-i18n': vueI18n }, rules: {
    '@intlify/vue-i18n/no-raw-text': ['error', { attributes: { '/.+/': ['label', 'placeholder', 'title', 'aria-label', 'alt'] }, ignoreText: ['—'] }],
  } },
)
