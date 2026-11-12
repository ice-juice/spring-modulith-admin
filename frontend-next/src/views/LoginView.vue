<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, ShieldCheck } from '@lucide/vue'
import { useAuth } from '@/stores/auth'
import { useBranding } from '@/stores/branding'
import { get } from '@/api/http'
import { safeRedirect } from '@/router/access'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import DisplaySettings from '@/components/DisplaySettings.vue'
import BrandingFooter from '@/components/BrandingFooter.vue'
import TenantLoginLayout from '@/components/tenant/TenantLoginLayout.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
const { t } = useI18n(), route = useRoute(), router = useRouter(), auth = useAuth(), branding = useBranding()
const form = reactive({ username: '', password: '', captcha: '', captchaId: '', remember: false, portal: '' })
const busy = ref(false), error = ref(''), captchaImage = ref(''), captchaBusy = ref(false)
let captchaAttempt = 0, submission = 0, live = true, handoff = false
let cancelAuthentication: (() => void) | undefined
watch(() => branding.generation, () => {
  captchaAttempt++; captchaBusy.value = false; captchaImage.value = ''; form.captchaId = ''; form.captcha = ''
  if (busy.value && !handoff) { submission++; cancelAuthentication?.(); busy.value = false; form.password = '' }
}, { flush: 'sync' })
const invalidated = () => { submission++; busy.value = false; form.password = ''; form.captcha = ''; error.value = t('auth.verificationExpired') }
window.addEventListener('sma:authentication-invalidated', invalidated)
onUnmounted(() => { live = false; submission++; captchaAttempt++; if (busy.value && !handoff) cancelAuthentication?.(); form.password = ''; form.captcha = ''; window.removeEventListener('sma:authentication-invalidated', invalidated) })
async function refreshCaptcha() {
  if (!branding.ready || !branding.captchaEnabled || captchaBusy.value) return
  const current = ++captchaAttempt, entryVersion = branding.generation
  captchaBusy.value = true
  try {
    const result = await get<{ id: string; image: string }>('/auth/captcha')
    if (current !== captchaAttempt || entryVersion !== branding.generation || !branding.ready) return
    form.captchaId = result.id; captchaImage.value = result.image; form.captcha = ''
  } catch (cause) { if (current === captchaAttempt) error.value = (cause as Error).message }
  finally { if (current === captchaAttempt) captchaBusy.value = false }
}
async function submit() {
  if (busy.value) return
  if (!branding.passwordEnabled) { error.value = t('common.entryUnavailable'); return }
  error.value = ''
  if (!form.username.trim() || !form.password || (branding.captchaEnabled && !form.captcha)) { error.value = t('auth.required'); return }
  busy.value = true
  handoff = false
  const current = ++submission, entryVersion = branding.generation
  const owns = () => live && current === submission && entryVersion === branding.generation
  try {
    const pending = auth.login({ ...form, portal: branding.portal })
    cancelAuthentication = auth.captureAuthentication()
    const status = await pending
    if (!owns()) return
    form.password = ''
    handoff = true
    await router.replace(status === 'MFA_REQUIRED' ? { path: '/login/totp', query: { redirect: safeRedirect(route.query.redirect) } } : safeRedirect(route.query.redirect))
  } catch (cause) { if (owns()) { error.value = (cause as Error).message; await refreshCaptcha() } }
  finally { if (owns()) { busy.value = false; form.password = ''; form.captcha = '' } }
}
async function loadEntry() {
  const loaded = await branding.load()
  if (live && loaded) { form.portal = branding.portal; await refreshCaptcha() }
}
onMounted(loadEntry)
</script>
<template>
  <TenantLoginLayout :template="branding.template" :name="branding.name" :title="branding.welcomeTitle" :description="branding.welcomeDescription" :logo="branding.logo" :background="branding.background" :blur="branding.entry?.branding.login.backgroundBlur" :accent="branding.accent">
    <template #tools><DisplaySettings /></template>
    <h2 class="text-3xl font-semibold tracking-tight">{{ t('auth.loginTitle') }}</h2><p class="mt-3 mb-8 text-sm text-muted-foreground">{{ t('auth.loginHint') }}</p>
    <p v-if="route.query.securityUpdated" role="status" class="mb-4 text-sm text-muted-foreground">{{ t('auth.securityChanged') }}</p><ErrorNotice :message="branding.error || error" /><p v-if="branding.loading" role="status">{{ t('common.loading') }}</p><Button v-if="branding.error" type="button" variant="outline" :disabled="busy || branding.loading" @click="loadEntry">{{ t('common.refresh') }}</Button><form class="space-y-5" @submit.prevent="submit"><div class="space-y-2"><Label for="username">{{ t('auth.username') }}</Label><Input id="username" v-model="form.username" name="username" autocomplete="username" maxlength="64" :disabled="busy || !branding.passwordEnabled" required /></div><div class="space-y-2"><Label for="password">{{ t('auth.password') }}</Label><PasswordInput id="password" v-model="form.password" name="password" autocomplete="current-password" maxlength="128" :disabled="busy || !branding.passwordEnabled" required /></div><div v-if="branding.captchaEnabled" class="space-y-2"><Label for="captcha">{{ t('auth.captcha') }}</Label><div class="flex gap-3"><Input id="captcha" v-model="form.captcha" name="captcha" autocomplete="off" maxlength="8" :disabled="busy || !branding.passwordEnabled" required /><Button type="button" variant="outline" class="h-9 w-28 shrink-0 overflow-hidden p-0" :disabled="captchaBusy || !branding.ready" :aria-label="t('auth.refreshCaptcha')" @click="refreshCaptcha"><img v-if="captchaImage" :src="captchaImage" :alt="t('auth.captcha')" class="h-full w-full object-contain" /><span v-else>{{ t('common.refresh') }}</span></Button></div></div><div class="flex items-center gap-2"><Checkbox id="remember" v-model="form.remember" :disabled="busy || !branding.passwordEnabled" /><Label for="remember" class="font-normal">{{ t('auth.remember') }}</Label></div><Button type="submit" class="mt-2 w-full" :disabled="busy || !branding.passwordEnabled">{{ t(busy ? 'common.loading' : 'auth.signIn') }}<ArrowRight class="size-4" /></Button></form><p class="mt-6 flex items-center justify-center gap-2 text-xs text-muted-foreground"><ShieldCheck class="size-4 shrink-0" />{{ t('common.securityHint') }}</p>
    <template #footer><BrandingFooter class="justify-center" /></template>
  </TenantLoginLayout>
</template>
