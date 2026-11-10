<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { get } from '@/api/http'
import { safeRedirect } from '@/router/access'
import DisplaySettings from '@/components/DisplaySettings.vue'
import { useBranding } from '@/stores/branding'
import BrandingFooter from '@/components/BrandingFooter.vue'
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
  portal: String(route.query.portal || 'admin'),
})
const captchaImage = ref('')
const busy = ref(false)
const error = ref('')
async function refreshCaptcha() {
  if (!branding.captchaEnabled) return
  try {
    const result = await get<{ id: string; image: string }>('/auth/captcha')
    form.captchaId = result.id
    captchaImage.value = result.image
    form.captcha = ''
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function submit() {
  if (!form.username || !form.password || (branding.captchaEnabled && !form.captcha)) {
    error.value = t('auth.required')
    return
  }
  busy.value = true
  error.value = ''
  try {
    await auth.login(form)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = (e as Error).message
    await refreshCaptcha()
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  await branding.load()
  await refreshCaptcha()
})
</script>
<template>
  <div class="login-page">
    <div
      v-if="branding.background"
      class="login-background"
      :style="{
        backgroundImage:
          'linear-gradient(#122237aa,#122237cc),url(' + JSON.stringify(branding.background) + ')',
        filter: branding.values['login.backgroundBlur'] === 'true' ? 'blur(8px)' : 'none',
      }"
    />
    <div class="login-tools"><DisplaySettings /></div>
    <section class="login-brand">
      <div class="brand-mark">{{ t('common.shortBrand') }}</div>
      <h1>{{ branding.name }}</h1>
      <p>{{ t('common.workspace') }}</p>
    </section>
    <el-card class="login-card" shadow="never">
      <h2>{{ t('auth.loginTitle') }}</h2>
      <p class="muted">{{ t('auth.loginHint') }}</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-form label-position="top" @submit.prevent="submit">
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
              :aria-label="t('auth.refreshCaptcha')"
              @click="refreshCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" :alt="t('auth.captcha')" />
            </button></div
        ></el-form-item>
        <el-checkbox v-model="form.remember">{{ t('auth.remember') }}</el-checkbox>
        <el-button class="login-submit" type="primary" native-type="submit" :loading="busy">{{
          t('auth.signIn')
        }}</el-button>
      </el-form>
    </el-card>
    <BrandingFooter class="login-footer" />
  </div>
</template>
