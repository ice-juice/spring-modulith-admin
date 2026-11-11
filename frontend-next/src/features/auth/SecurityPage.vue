<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAccountSecurity } from '@/composables/useAccountSecurity'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/business/PageHeader.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
const { t } = useI18n()
const { settings, mode, pending, busy, error, uncertain, qr, form, remaining, needsCode, passwordMode, reload, open, close, submit, confirm, cancel } = useAccountSecurity()
</script>
<template>
  <PageHeader :title="t('auth.accountSecurity')"><Button variant="outline" :disabled="busy" @click="reload">{{ t('auth.refreshSecurity') }}</Button></PageHeader>
  <div class="security-page">
    <ErrorNotice :message="error" />
    <p v-if="settings && !settings.setupAvailable" role="status" class="text-sm text-muted-foreground">{{ t('auth.securityUnavailable') }}</p>
    <div v-if="settings" class="security-layout" :class="{ 'security-layout--active': mode || pending }">
      <div class="security-summary">
        <Card class="security-summary-card" :class="{ 'security-summary-card--active': pending || mode === 'enroll' || mode === 'disable' }">
          <CardContent class="space-y-4 px-5">
            <div class="flex items-center gap-3">
              <span class="security-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8 12 3 3 5-6" /></svg></span>
              <h2 class="font-semibold">{{ t('auth.totpTitle') }}</h2>
            </div>
            <p class="security-status" :class="{ 'security-status--enabled': settings.totpEnabled }"><span aria-hidden="true" />{{ t(settings.totpEnabled ? 'auth.totpEnabled' : 'auth.totpDisabled') }}</p>
            <p class="security-description">{{ t('auth.totpDescription') }}</p>
            <p v-if="settings.totpPending && !pending" class="text-sm">{{ t('auth.securityPending') }}</p>
            <div class="security-actions"><Button variant="outline" :disabled="busy || uncertain || !settings.setupAvailable || !!pending" @click="open('enroll')">{{ t(settings.totpEnabled ? 'auth.replaceTotp' : 'auth.bindTotp') }}</Button><Button v-if="settings.totpEnabled" variant="ghost" :disabled="busy || uncertain || !settings.setupAvailable || !!pending" @click="open('disable')">{{ t('auth.disableTotp') }}</Button></div>
          </CardContent>
        </Card>
        <Card class="security-summary-card" :class="{ 'security-summary-card--active': passwordMode }">
          <CardContent class="space-y-4 px-5">
            <div class="flex items-center gap-3">
              <span class="security-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="8" cy="9" r="5" /><path d="m11.5 12.5 8 8M16 17l3-3M18 19l3-3" /></svg></span>
              <h2 class="font-semibold">{{ t('auth.securityPassword') }}</h2>
            </div>
            <p class="security-status" :class="{ 'security-status--enabled': settings.securityPasswordSet }"><span aria-hidden="true" />{{ t(settings.securityPasswordSet ? 'auth.securityPasswordSet' : 'auth.securityPasswordUnset') }}</p>
            <p class="security-description">{{ t('auth.securityPasswordHint') }}</p>
            <div class="security-actions"><Button variant="outline" :disabled="busy || uncertain || !settings.setupAvailable || !!pending" @click="open(settings.securityPasswordSet ? 'change' : 'set')">{{ t(settings.securityPasswordSet ? 'auth.changeSecurityPassword' : 'auth.setSecurityPassword') }}</Button><Button v-if="settings.securityPasswordSet && settings.totpEnabled" variant="ghost" :disabled="busy || uncertain || !settings.setupAvailable || !!pending" @click="open('reset')">{{ t('auth.resetSecurityPassword') }}</Button></div>
            <p v-if="settings.securityPasswordSet" class="security-description">{{ t('auth.securityResetHint') }}</p>
          </CardContent>
        </Card>
      </div>

      <Card class="security-workspace">
        <CardContent class="security-workspace-content">
          <template v-if="pending">
            <header class="security-section-header">
              <div><p class="security-eyebrow">{{ t('auth.totpTitle') }}</p><h2 class="security-title">{{ t('auth.confirmEnrollment') }}</h2></div>
              <span class="security-timer" role="timer" aria-live="off">{{ t('auth.secondsLeft', { seconds: remaining }) }}</span>
            </header>
            <p class="security-description">{{ t('auth.scanAuthenticator') }}</p>
            <div class="security-enrollment">
              <div class="security-qr"><img v-if="qr" :src="qr" :alt="t('auth.authenticatorQr')" width="220" height="220" /></div>
              <div class="min-w-0 space-y-5">
                <div class="space-y-2"><Label for="manual-secret">{{ t('auth.manualSecret') }}</Label><Input id="manual-secret" class="font-mono text-xs" :model-value="pending.secret" readonly autocomplete="off" spellcheck="false" /></div>
                <form class="space-y-5" @submit.prevent="confirm">
                  <div class="space-y-2"><Label for="new-authenticator-code">{{ t('auth.newAuthenticatorCode') }}</Label><Input id="new-authenticator-code" v-model="form.newCode" class="security-code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" :disabled="busy" /></div>
                  <div class="security-actions"><Button type="submit" :disabled="busy || uncertain || !remaining">{{ t('auth.confirmEnrollment') }}</Button><Button type="button" variant="outline" :disabled="busy || uncertain" @click="cancel">{{ t('auth.cancelEnrollment') }}</Button></div>
                </form>
              </div>
            </div>
          </template>
          <form v-else-if="mode" class="security-form" @submit.prevent="submit">
            <header><p class="security-eyebrow">{{ t(passwordMode ? 'auth.securityPassword' : 'auth.totpTitle') }}</p><h2 class="security-title">{{ t(mode === 'disable' ? 'auth.disableTotpConfirm' : mode === 'enroll' ? (settings.totpEnabled ? 'auth.replaceTotp' : 'auth.bindTotp') : mode === 'set' ? 'auth.setSecurityPassword' : mode === 'change' ? 'auth.changeSecurityPassword' : 'auth.resetSecurityPassword') }}</h2></header>
            <p class="security-description">{{ t('auth.securityWriteHint') }}</p>
            <div class="space-y-2"><Label for="security-login-password">{{ t('auth.loginPassword') }}</Label><PasswordInput id="security-login-password" v-model="form.password" maxlength="128" autocomplete="current-password" :disabled="busy" /></div>
            <div v-if="needsCode" class="space-y-2"><Label for="security-code">{{ t('auth.totpCode') }}</Label><Input id="security-code" v-model="form.code" class="security-code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" :disabled="busy" /></div>
            <div v-if="mode === 'change'" class="space-y-2"><Label for="security-old">{{ t('auth.oldSecurityPassword') }}</Label><PasswordInput id="security-old" v-model="form.oldPassword" maxlength="8" autocomplete="off" :disabled="busy" /></div>
            <div v-if="passwordMode" class="security-password-fields">
              <div class="space-y-2"><Label for="security-new">{{ t('auth.newSecurityPassword') }}</Label><PasswordInput id="security-new" v-model="form.newPassword" maxlength="8" autocomplete="new-password" :disabled="busy" /></div>
              <div class="space-y-2"><Label for="security-confirm">{{ t('auth.confirmSecurityPassword') }}</Label><PasswordInput id="security-confirm" v-model="form.confirmation" maxlength="8" autocomplete="new-password" :disabled="busy" /></div>
            </div>
            <div class="security-actions border-t pt-5"><Button type="submit" :disabled="busy || uncertain">{{ t(busy ? 'common.loading' : 'auth.saveSecurity') }}</Button><Button type="button" variant="ghost" :disabled="busy" @click="close">{{ t('auth.closeSecurityForm') }}</Button></div>
          </form>
          <div v-else class="security-idle">
            <span class="security-icon security-icon--large" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="5" y="10" width="14" height="11" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3" /></svg></span>
            <h2 class="security-title">{{ t('auth.accountSecurity') }}</h2>
            <p class="security-description">{{ t('auth.securityWorkspaceHint') }}</p>
            <p class="security-description">{{ t('auth.securityWriteHint') }}</p>
          </div>
          <p class="security-note">{{ t('auth.securityNoRecovery') }}</p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>

<style scoped>
.security-page { container-type: inline-size; display: grid; gap: 1rem; width: 100%; }
.security-layout, .security-summary { display: grid; gap: 1rem; min-width: 0; align-items: start; }
.security-summary-card { padding-block: 1.25rem; transition: border-color .15s; }
.security-summary-card--active { border-color: var(--primary); box-shadow: 0 0 0 1px color-mix(in srgb, var(--primary) 12%, transparent); }
.security-icon { display: grid; place-items: center; flex: none; width: 2.5rem; height: 2.5rem; border-radius: .8rem; background: var(--accent); color: var(--primary); }
.security-icon svg { width: 1.4rem; height: 1.4rem; }
.security-icon--large { width: 3.5rem; height: 3.5rem; border-radius: 1.1rem; }
.security-icon--large svg { width: 2rem; height: 2rem; }
.security-status { display: flex; align-items: center; gap: .5rem; font-size: .75rem; line-height: 1.5; color: var(--muted-foreground); }
.security-status > span { width: .4rem; height: .4rem; border-radius: 50%; background: currentColor; flex: none; }
.security-status--enabled { color: var(--primary); }
.security-description { color: var(--muted-foreground); font-size: .875rem; line-height: 1.8; overflow-wrap: anywhere; }
.security-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.security-actions :deep(button) { height: auto; min-height: 2.5rem; white-space: normal; }
.security-workspace { min-width: 0; padding-block: 1.5rem; }
.security-workspace-content { display: grid; gap: 1.25rem; min-width: 0; padding-inline: 1.25rem; }
.security-section-header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: .75rem; }
.security-eyebrow { margin-bottom: .375rem; color: var(--muted-foreground); font-size: .75rem; }
.security-title { font-size: 1.125rem; font-weight: 600; line-height: 1.5; }
.security-timer { padding: .375rem .625rem; border: 1px solid var(--border); border-radius: .5rem; font-size: .75rem; color: var(--muted-foreground); font-variant-numeric: tabular-nums; }
.security-enrollment { display: grid; gap: 1.5rem; align-items: start; }
.security-qr { justify-self: center; width: 236px; max-width: 100%; padding: 7px; background: white; border: 1px solid var(--border); border-radius: .75rem; }
.security-qr img { display: block; width: 100%; height: auto; aspect-ratio: 1; }
.security-code { font-variant-numeric: tabular-nums; letter-spacing: .3em; }
.security-form { display: grid; gap: 1.25rem; }
.security-password-fields { display: grid; gap: 1.25rem; }
.security-idle { display: grid; justify-items: center; text-align: center; gap: 1rem; padding: 1.5rem 0; }
.security-note { padding: 1rem; border-radius: .75rem; background: var(--muted); color: var(--muted-foreground); font-size: .75rem; line-height: 1.8; }
@container (min-width: 840px) {
  .security-layout { grid-template-columns: 320px minmax(0, 1fr); gap: 1.5rem; }
  .security-workspace-content { padding-inline: 1.75rem; }
  .security-idle { padding-block: 3rem; }
  .security-password-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@container (min-width: 960px) {
  .security-enrollment { grid-template-columns: 236px minmax(0, 1fr); }
}
@container (max-width: 839px) {
  .security-layout--active .security-workspace { grid-row: 1; }
}
@media (prefers-reduced-motion: reduce) { .security-summary-card { transition: none; } }
</style>
