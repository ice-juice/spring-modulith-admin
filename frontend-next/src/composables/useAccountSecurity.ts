import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { ApiError, get, post, put } from '@/api/http'
import { getToken } from '@/lib/token'
import { validSecurityPassword } from '@/lib/password'
import { useAuth } from '@/stores/auth'
import { credentialOutcomeUnknown, useCredentialIntents } from '@/stores/credential-intents'

export interface SecuritySettings { totpEnabled: boolean; totpPending: boolean; totpEnabledAt: string | null; securityPasswordSet: boolean; securityPasswordChangedAt: string | null; setupAvailable: boolean }
interface Enrollment { challengeToken: string; secret: string; uri: string; expiresAt: string; replacing: boolean }
export type SecurityMode = 'enroll' | 'disable' | 'set' | 'change' | 'reset' | null

export function useAccountSecurity() {
  const { t } = useI18n(), router = useRouter(), auth = useAuth(), intents = useCredentialIntents()
  const settings = ref<SecuritySettings | null>(null), mode = ref<SecurityMode>(null), pending = ref<Enrollment | null>(null)
  const working = ref(false), message = ref(''), qr = ref(''), now = ref(Date.now())
  const busy = computed(() => working.value || intents.busy)
  const uncertain = computed(() => intents.unresolved || intents.unavailable)
  const error = computed(() => intents.problem || message.value || (intents.unavailable ? t('common.operation.storage') : !busy.value && uncertain.value ? t('auth.securityUncertain') : ''))
  const form = reactive({ password: '', code: '', oldPassword: '', newPassword: '', confirmation: '', newCode: '' })
  let mounted = true, revision = 0, timer: ReturnType<typeof setInterval> | undefined
  const remaining = computed(() => pending.value ? Math.max(0, Math.ceil((Date.parse(pending.value.expiresAt) - now.value) / 1000)) : 0)
  const needsCode = computed(() => !!settings.value?.totpEnabled)
  const passwordMode = computed(() => mode.value === 'set' || mode.value === 'change' || mode.value === 'reset')
  function clearInputs() { Object.assign(form, { password: '', code: '', oldPassword: '', newPassword: '', confirmation: '', newCode: '' }) }
  function discard() { mode.value = null; pending.value = null; qr.value = ''; clearInputs() }
  function open(next: SecurityMode) { if (busy.value || uncertain.value || auth.user?.locked) return; discard(); message.value = ''; mode.value = next }
  function close() { if (!busy.value) { discard(); message.value = '' } }
  async function reload() {
    if (busy.value || !auth.user || !getToken() || auth.user.locked) return
    working.value = true; message.value = ''; const current = ++revision, token = getToken()
    const owns = () => mounted && revision === current && token === getToken() && !auth.user?.locked
    try {
      const result = await get<SecuritySettings>('/auth/security-settings', undefined, { expectedIdentity: token })
      if (!owns()) return
      settings.value = result; discard() // Read-only status is never completion evidence for the pending write.
    } catch (cause) { if (owns()) message.value = cause instanceof ApiError && cause.code === 423 ? '' : (cause as Error).message }
    finally { if (mounted && current === revision) working.value = false }
  }
  async function signedOut() { discard(); auth.reset(); await router.replace({ path: '/login', query: { securityUpdated: '1' } }) }
  type Send = <T = unknown>(method: 'post' | 'put', path: string, input: unknown) => Promise<T>
  async function mutate(action: (current: () => boolean, send: Send) => Promise<void>) {
    if (busy.value || uncertain.value || !auth.user || !getToken() || auth.user.locked) return
    working.value = true; message.value = ''
    const token = getToken(), sequence = revision
    const current = () => mounted && getToken() === token && sequence === revision && !auth.user?.locked
    let ticket: Awaited<ReturnType<typeof intents.begin>> = null
    try {
      ticket = await intents.begin()
      if (!ticket || !current()) { if (current()) message.value = t(intents.unavailable ? 'common.operation.storage' : 'auth.securityUncertain'); return }
      const owned = ticket
      const send: Send = async <T>(method: 'post' | 'put', path: string, input: unknown) => {
          if (!current() || !await intents.sent(owned) || !current()) throw new Error(intents.problem || t('auth.securityUncertain'))
        try {
          const result = await (method === 'post' ? post<T> : put<T>)(path, input, { expectedIdentity: owned.token, idempotencyKey: owned.key })
          intents.settle(owned, true) // A later QR or route failure cannot undo a directly confirmed server result.
          return result
        } catch (cause) { intents.settle(owned, !credentialOutcomeUnknown(cause)); throw cause }
      }
      await action(current, send)
    }
    catch (cause) {
      if (!current()) return
      if (cause instanceof ApiError && cause.code === 401) { auth.reset(); await router.replace('/login'); return }
      message.value = uncertain.value ? t('auth.securityUncertain') : (cause as Error).message
    } finally {
      if (ticket) await intents.release(ticket)
      if (mounted && sequence === revision) {
        if (token !== getToken()) { discard(); settings.value = null } else clearInputs()
        working.value = false
      }
    }
  }
  async function submit() {
    if (busy.value || uncertain.value) return
    if (!settings.value || !mode.value || !form.password || (needsCode.value && !/^[0-9]{6}$/.test(form.code)) ||
      (passwordMode.value && (!validSecurityPassword(form.newPassword) || form.newPassword !== form.confirmation || (mode.value === 'change' && !form.oldPassword)))) {
      message.value = t('auth.securityInputError'); return
    }
    const action = mode.value
    const input = { password: form.password, code: needsCode.value ? form.code : undefined, oldPassword: form.oldPassword || undefined, newPassword: form.newPassword, confirmation: form.confirmation }
    await mutate(async (current, send) => {
      if (action === 'enroll') {
        const result = await send<Enrollment>('post', '/auth/totp-enrollments', { password: input.password, code: input.code })
        if (!current()) return
        pending.value = result; mode.value = null
        // Generated locally; the provisioning secret never leaves this origin for QR rendering.
        const image = await QRCode.toDataURL(result.uri, { width: 220, margin: 2, errorCorrectionLevel: 'M' })
        if (current()) qr.value = image
      } else {
        if (action === 'disable') await send('post', '/auth/totp-credentials/disable', { password: input.password, code: input.code })
        else if (action === 'set') await send('post', '/auth/security-passwords', input)
        else if (action === 'change') await send('put', '/auth/security-passwords', input)
        else await send('post', '/auth/security-passwords/reset', input)
        if (current()) await signedOut()
      }
    })
  }
  async function confirm() {
    if (busy.value || uncertain.value) return
    if (!pending.value || !remaining.value || !/^[0-9]{6}$/.test(form.newCode)) { message.value = t('auth.securityInputError'); return }
    const input = { challengeToken: pending.value.challengeToken, code: form.newCode }
    await mutate(async (current, send) => { await send('post', '/auth/totp-enrollments/confirm', input); if (current()) await signedOut() })
  }
  async function cancel() {
    if (busy.value || uncertain.value) return
    if (!pending.value) return
    const token = pending.value.challengeToken
    await mutate(async (current, send) => { await send('post', '/auth/totp-enrollments/cancel', { challengeToken: token }); if (current()) { discard(); if (settings.value) settings.value.totpPending = false } })
  }
  onMounted(() => {
    void reload()
    timer = setInterval(() => {
      now.value = Date.now()
      if (pending.value && remaining.value === 0 && !busy.value) { discard(); message.value = t('auth.enrollmentExpired') }
    }, 1000)
  })
  watch(() => auth.user ? `${auth.user.id}:${auth.user.portal}:${getToken()}` : '', () => { revision++; discard(); settings.value = null; working.value = false; message.value = '' }, { flush: 'sync' })
  watch(() => intents.unresolved, value => { if (value && !working.value) { revision++; discard(); message.value = '' } }, { flush: 'sync' })
  watch(() => auth.user?.locked, (locked, previous) => {
    if (locked) { revision++; discard(); settings.value = null; message.value = ''; working.value = false }
    else if (previous === true && locked === false) void reload()
  }, { flush: 'sync' })
  onBeforeUnmount(() => { mounted = false; revision++; clearInterval(timer); discard() })
  return { settings, mode, pending, busy, error, uncertain, qr, form, remaining, needsCode, passwordMode, reload, open, close, submit, confirm, cancel }
}
