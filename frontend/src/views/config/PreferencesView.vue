<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { get, put, remove } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { useAuth } from '@/stores/auth'
import { normalizeLocale, type AppLocale } from '@/locales'
const { t } = useI18n()
const preferences = usePreferences()
const form = reactive({
  locale: preferences.locale,
  timeZone: preferences.timeZone,
  theme: preferences.theme,
  tabsEnabled: preferences.tabsEnabled,
  keepTabsAfterReload: preferences.keepTabsAfterReload,
})
const zones = ['UTC', ...Intl.supportedValuesOf('timeZone')]
const error = ref(''),
  busy = ref(false)
async function load() {
  Object.assign(form, await get('/config/preferences'))
  if (!form.locale) form.locale = preferences.locale
  if (!form.timeZone) form.timeZone = preferences.timeZone
}
async function save() {
  busy.value = true
  error.value = ''
  try {
    await put('/config/preferences', form)
    preferences.changeLocale(form.locale as AppLocale)
    preferences.changeTimeZone(form.timeZone)
    preferences.applyAppearance(form)
    await useAuth().load(false)
    ElMessage.success(t('common.saved'))
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function reset() {
  await remove('/config/preferences')
  preferences.changeLocale(normalizeLocale(navigator.language))
  await useAuth().load()
  Object.assign(form, preferences.snapshot())
  ElMessage.success(t('common.saved'))
}
onMounted(load)
</script>
<template>
  <el-card shadow="never" style="max-width: 720px">
    <h2>{{ t('common.preferences') }}</h2>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-width="180px">
      <el-form-item :label="t('common.language')"
        ><el-select v-model="form.locale"
          ><el-option :label="'简体中文'" value="zh-CN" /><el-option
            :label="'English'"
            value="en-US" /></el-select
      ></el-form-item>
      <el-form-item :label="t('common.timeZone')"
        ><el-select v-model="form.timeZone" filterable
          ><el-option v-for="zone in zones" :key="zone" :label="zone" :value="zone" /></el-select
      ></el-form-item>
      <el-form-item :label="t('config.theme')"
        ><el-select v-model="form.theme"
          ><el-option
            v-for="theme in ['light', 'dark', 'system']"
            :key="theme"
            :label="t('config.themes.' + theme)"
            :value="theme" /></el-select
      ></el-form-item>
      <el-form-item :label="t('config.tabsEnabled')"
        ><el-switch v-model="form.tabsEnabled"
      /></el-form-item>
      <el-form-item :label="t('config.keepTabs')"
        ><el-switch v-model="form.keepTabsAfterReload" :disabled="!form.tabsEnabled"
      /></el-form-item>
      <el-form-item
        ><el-button type="primary" :loading="busy" @click="save">{{ t('common.save') }}</el-button
        ><el-button @click="reset">{{ t('config.resetDefaults') }}</el-button></el-form-item
      >
    </el-form>
  </el-card>
</template>
