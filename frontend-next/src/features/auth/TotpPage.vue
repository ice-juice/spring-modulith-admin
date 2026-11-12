<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useBranding } from '@/stores/branding'
import TenantLoginLayout from '@/components/tenant/TenantLoginLayout.vue'
import BrandingFooter from '@/components/BrandingFooter.vue'
import { safeRedirect } from '@/router/access'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import DisplaySettings from '@/components/DisplaySettings.vue'
const branding = useBranding(), auth = useAuth(), route = useRoute(), router = useRouter(), { t } = useI18n()
const code = ref(''), busy = ref(false), error = ref(''), now = ref(Date.now())
const remaining = computed(() => auth.pendingMfa ? Math.max(0, Math.ceil((Date.parse(auth.pendingMfa.expiresAt) - now.value) / 1000)) : 0)
let timer: ReturnType<typeof setInterval> | undefined
let live = true, submission = 0, handoff = false
let cancelAuthentication = auth.captureAuthentication()
async function restart() { cancelAuthentication(); await router.replace('/login') }
async function verify() {
  if (busy.value) return
  if (!branding.ready) { error.value = t('common.entryUnavailable'); return }
  if (!remaining.value) { error.value = t('auth.verificationExpired'); return }
  if (!/^[0-9]{6}$/.test(code.value)) { error.value = t('auth.securityInputError'); return }
  busy.value = true; error.value = ''
  handoff = false
  const current = ++submission, entryVersion = branding.generation
  const owns = () => live && current === submission && entryVersion === branding.generation
  try { const pending = auth.verifyMfa(code.value); cancelAuthentication = auth.captureAuthentication(); await pending; if (owns()) { handoff = true; await router.replace(safeRedirect(route.query.redirect)) } }
  catch (cause) { if (owns()) error.value = (cause as Error).message }
  finally { if (owns()) { code.value = ''; busy.value = false } }
}
watch(() => branding.generation, () => { if (busy.value && !handoff) { submission++; cancelAuthentication(); busy.value = false; code.value = '' } }, { flush: 'sync' })
const invalidated = () => { submission++; busy.value = false; code.value = ''; error.value = t('auth.verificationExpired') }
window.addEventListener('sma:authentication-invalidated', invalidated)
onMounted(() => { timer = setInterval(() => { now.value = Date.now() }, 1000); if (!auth.pendingMfa) error.value = t('auth.verificationExpired') })
onBeforeUnmount(() => { live = false; submission++; clearInterval(timer); code.value = ''; if (!handoff && (busy.value || auth.pendingMfa)) cancelAuthentication(); window.removeEventListener('sma:authentication-invalidated', invalidated) })
</script>
<template>
  <TenantLoginLayout :template="branding.template" :name="branding.name" :title="branding.welcomeTitle" :description="branding.welcomeDescription" :logo="branding.logo" :background="branding.background" :blur="branding.entry?.branding.login.backgroundBlur" :accent="branding.accent">
    <template #tools><DisplaySettings /></template>
    <section class="space-y-5">
      <h1 class="text-2xl font-semibold">{{ t('auth.totpTitle') }}</h1>
      <p class="text-sm text-muted-foreground">{{ t('auth.totpHint') }}</p>
      <ErrorNotice :message="branding.error || error" />
      <form class="space-y-4" @submit.prevent="verify">
        <div class="space-y-2"><Label for="totp-code">{{ t('auth.totpCode') }}</Label><Input id="totp-code" v-model="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" :disabled="busy || !remaining || !branding.ready" autofocus /></div>
        <p class="text-sm text-muted-foreground" role="timer" aria-live="off">{{ remaining ? t('auth.secondsLeft', { seconds: remaining }) : t('auth.verificationExpired') }}</p>
        <Button type="submit" class="w-full" :disabled="busy || !remaining || !branding.ready">{{ t(busy ? 'common.loading' : 'auth.verifyCode') }}</Button>
      </form>
      <Button variant="ghost" class="w-full" :disabled="busy" @click="restart">{{ t('auth.restartLogin') }}</Button>
    </section>
    <template #footer><BrandingFooter class="justify-center" /></template>
  </TenantLoginLayout>
</template>
