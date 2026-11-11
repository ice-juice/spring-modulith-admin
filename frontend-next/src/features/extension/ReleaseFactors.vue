<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { releaseIdentity, releaseRequest } from './client'
const { t } = useI18n(), busy = ref(false), error = ref(''), secret = ref(''), recovery = ref<string[]>([])
const operationPassword = ref(''), enrollmentToken = ref(''), otp = ref('')
const recovering = ref(!!releaseIdentity.value?.enrolled), recoveryCode = ref('')
const emit = defineEmits<{ completed: [] }>()
function wipe() { operationPassword.value = ''; enrollmentToken.value = ''; otp.value = ''; secret.value = ''; recovery.value = []; recoveryCode.value = '' }
onBeforeUnmount(wipe)
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const proof = { operationPassword: operationPassword.value, enrollmentToken: enrollmentToken.value, otp: otp.value }
    if (recovering.value) {
      const ticket = await releaseRequest<{ enrollmentToken: string }>('POST', '/console/recovery', { operationPassword: operationPassword.value, recoveryCode: recoveryCode.value })
      enrollmentToken.value = ticket.enrollmentToken; recoveryCode.value = ''; recovering.value = false
    }
    else if (!secret.value) secret.value = (await releaseRequest<{ secret: string }>('POST', '/console/enrollment/begin', proof)).secret
    else {
      recovery.value = await releaseRequest<string[]>('POST', '/console/enrollment/complete', proof)
      secret.value = ''; operationPassword.value = ''; enrollmentToken.value = ''
    }
  } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false; otp.value = '' }
}
</script>
<template>
  <form class="space-y-4 rounded-xl border bg-card p-4" @submit.prevent="submit">
    <ErrorNotice :message="error" />
    <template v-if="!recovery.length">
      <p class="text-sm text-muted-foreground">{{ t('extension.enrollmentHint') }}</p>
      <div v-if="!recovering" class="space-y-2"><Label for="release-enrollment-ticket">{{ t('extension.enrollmentToken') }}</Label><PasswordInput id="release-enrollment-ticket" v-model="enrollmentToken" required autocomplete="off" :disabled="busy || !!secret" /></div>
      <div v-else class="space-y-2"><Label for="release-factor-recovery">{{ t('extension.recoveryCode') }}</Label><PasswordInput id="release-factor-recovery" v-model="recoveryCode" required autocomplete="off" :disabled="busy" /></div>
      <div class="space-y-2"><Label for="release-enrollment-password">{{ t('extension.fields.operationPassword') }}</Label><PasswordInput id="release-enrollment-password" v-model="operationPassword" required autocomplete="off" :disabled="busy" /></div>
      <div v-if="secret" class="rounded-lg border bg-muted p-3"><p class="mb-2 text-sm">{{ t('extension.secretOnce') }}</p><code class="break-all select-all">{{ secret }}</code></div>
      <div v-if="secret" class="space-y-2"><Label for="release-enrollment-otp">{{ t('extension.otp') }}</Label><Input id="release-enrollment-otp" v-model="otp" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code" required :disabled="busy" /></div>
      <Button type="submit" :disabled="busy">{{ busy ? t('common.loading') : t(recovering ? 'extension.recovery' : secret ? 'extension.confirmEnrollment' : 'extension.beginEnrollment') }}</Button>
    </template>
    <template v-else><p>{{ t('extension.recoveryOnce') }}</p><pre class="max-h-64 overflow-auto rounded-lg border p-3 text-xs">{{ recovery.join('\n') }}</pre><Button type="button" @click="wipe(); emit('completed')">{{ t('extension.savedRecovery') }}</Button></template>
  </form>
</template>
