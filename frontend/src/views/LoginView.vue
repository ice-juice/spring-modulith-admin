<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { get } from '@/api/http'
import { safeRedirect } from '@/router/access'
import DisplaySettings from '@/components/DisplaySettings.vue'
import { useBranding } from '@/stores/branding'
import BrandingFooter from '@/components/BrandingFooter.vue'
import TenantLoginLayout from '@/components/TenantLoginLayout.vue'
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const branding = useBranding()
const form = reactive({
  username: '',
  password: '',
  captcha: '',
  captchaId: '',
  remember: false,
  portal: '',
})
const captchaImage = ref('')
const busy = ref(false)
const error = ref('')
const captchaBusy = ref(false)
let captchaAttempt = 0
watch(() => branding.generation, () => {
  captchaAttempt++; captchaBusy.value = false; captchaImage.value = ''; form.captchaId = ''; form.captcha = ''
  if (busy.value) { auth.reset(); form.password = '' }
}, { flush: 'sync' })
onUnmounted(() => { captchaAttempt++ })
async function refreshCaptcha() {
  if (!branding.ready || !branding.captchaEnabled || captchaBusy.value) return
  const current = ++captchaAttempt, entryVersion = branding.generation
  captchaBusy.value = true
  try {
    const result = await get<{ id: string; image: string }>('/auth/captcha')
    if (current !== captchaAttempt || entryVersion !== branding.generation || !branding.ready) return
    form.captchaId = result.id
    captchaImage.value = result.image
    form.captcha = ''
  } catch (e) {
    if (current === captchaAttempt) error.value = (e as Error).message
  } finally {
    if (current === captchaAttempt) captchaBusy.value = false
  }
}
async function submit() {
  if (busy.value) return
  if (!branding.passwordEnabled) { error.value = t('common.entryUnavailable'); return }
  if (!form.username || !form.password || (branding.captchaEnabled && !form.captcha)) {
    error.value = t('auth.required')
    return
  }
  busy.value = true
  error.value = ''
  try {
    const status = await auth.login({ ...form, portal: branding.portal })
    form.password = ''
    await router.replace(status === 'MFA_REQUIRED' ? { path: '/login/totp', query: { redirect: safeRedirect(route.query.redirect) } } : safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = (e as Error).message
    await refreshCaptcha()
  } finally {
    busy.value = false
  }
}
async function loadEntry() {
  if (await branding.load()) { form.portal = branding.portal; await refreshCaptcha() }
}
onMounted(loadEntry)
</script>
<template>
  <TenantLoginLayout :template="branding.template" :name="branding.name" :title="branding.welcomeTitle" :description="branding.welcomeDescription" :logo="branding.logo" :background="branding.background" :blur="branding.entry?.branding.login.backgroundBlur" :accent="branding.accent">
    <template #tools><DisplaySettings /></template>
    <el-card class="login-card" shadow="never">
      <h2>{{ t('auth.loginTitle') }}</h2>
      <p class="muted">{{ t('auth.loginHint') }}</p>
      <el-alert v-if="route.query.securityUpdated" :title="t('auth.securityChanged')" type="success" :closable="false" />
      <el-alert v-if="branding.error || error" :title="branding.error || error" type="error" :closable="false" show-icon />
      <p v-if="branding.loading" role="status">{{ t('common.loading') }}</p>
      <el-button v-if="branding.error" :disabled="busy || branding.loading" @click="loadEntry">{{ t('common.refresh') }}</el-button>
      <el-form label-position="top" :disabled="busy || !branding.passwordEnabled" @submit.prevent="submit">
        <el-form-item :label="t('auth.username')"
          ><el-input v-model="form.username" name="username" autocomplete="username" maxlength="64"
        /></el-form-item>
        <el-form-item :label="t('auth.password')"
          ><el-input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            show-password
            maxlength="128"
        /></el-form-item>
        <el-form-item v-if="branding.captchaEnabled" :label="t('auth.captcha')"
          ><div class="captcha-row">
            <el-input
              v-model="form.captcha"
              name="captcha"
              autocomplete="off"
              maxlength="8"
            /><button
              type="button"
              class="captcha-image"
              :disabled="captchaBusy || !branding.ready"
              :aria-label="t('auth.refreshCaptcha')"
              @click="refreshCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" :alt="t('auth.captcha')" />
            </button></div
        ></el-form-item>
        <el-checkbox v-model="form.remember">{{ t('auth.remember') }}</el-checkbox>
        <el-button class="login-submit" type="primary" native-type="submit" :loading="busy" :disabled="busy || !branding.passwordEnabled">{{
          t('auth.signIn')
        }}</el-button>
      </el-form>
    </el-card>
    <template #footer><BrandingFooter /></template>
  </TenantLoginLayout>
</template>
