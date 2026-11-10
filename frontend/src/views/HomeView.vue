<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
const { t, locale } = useI18n()
const auth = useAuth()
const preferences = usePreferences()
const healthy = ref(false)
const now = ref(new Date().toISOString())
let timer: ReturnType<typeof setInterval>
onMounted(async () => {
  timer = setInterval(() => {
    now.value = new Date().toISOString()
  }, 1000)
  try {
    healthy.value = (await axios.get('/actuator/health')).data.status === 'UP'
  } catch {
    healthy.value = false
  }
})
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <section class="welcome-card">
    <div class="eyebrow">{{ t('common.workspace') }}</div>
    <h1>{{ t('common.welcome', { name: auth.user?.name || '' }) }}</h1>
    <p>{{ t('common.welcomeHint') }}</p>
  </section>
  <div class="dashboard-grid">
    <el-card shadow="never"
      ><div class="muted">{{ t('common.health') }}</div>
      <p>
        <el-tag :type="healthy ? 'success' : 'danger'">{{
          t(healthy ? 'common.healthy' : 'common.unavailable')
        }}</el-tag>
      </p></el-card
    >
    <el-card shadow="never"
      ><div class="muted">{{ t('common.currentTime') }}</div>
      <p class="metric-value">{{ formatInstant(now, preferences.timeZone, locale) }}</p>
      <small>{{ preferences.timeZone }}</small></el-card
    >
    <el-card shadow="never"
      ><div class="muted">{{ t('common.portal') }}</div>
      <p class="metric-value">{{ auth.user?.portal }}</p>
      <small>{{ auth.user?.username }}</small></el-card
    >
  </div>
</template>
