<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { usePreferences } from '@/stores/preferences'
import { useAuth } from '@/stores/auth'
import type { AppLocale } from '@/locales'
import { put } from '@/api/http'
import { ElMessage } from 'element-plus'
const { t } = useI18n()
const preferences = usePreferences()
const zones = Intl.supportedValuesOf('timeZone')
async function changeLanguage(value: AppLocale) {
  preferences.changeLocale(value)
  if (useAuth().user) {
    try {
      await put('/config/preferences', preferences.snapshot())
      await useAuth().load(false)
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
  }
}
async function changeZone(value: string) {
  preferences.changeTimeZone(value)
  if (useAuth().user)
    try {
      await put('/config/preferences', preferences.snapshot())
    } catch (e) {
      ElMessage.error((e as Error).message)
    }
}
</script>

<template>
  <div class="display-settings">
    <el-select
      :model-value="preferences.locale"
      :aria-label="t('common.language')"
      style="width: 115px"
      @change="changeLanguage"
    >
      <el-option :label="'简体中文'" value="zh-CN" />
      <el-option :label="'English'" value="en-US" />
    </el-select>
    <el-select
      :model-value="preferences.timeZone"
      filterable
      :aria-label="t('common.timeZone')"
      style="width: 205px"
      @change="changeZone"
    >
      <el-option :label="'UTC'" value="UTC" />
      <el-option v-for="zone in zones" :key="zone" :label="zone" :value="zone" />
    </el-select>
  </div>
</template>
