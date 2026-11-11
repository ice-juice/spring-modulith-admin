import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { ApiError, get, post, put } from '@/api/http'
import { getToken } from '@/lib/token'
import { validSecurityPassword } from '@/lib/password'
import { useAuth } from '@/stores/auth'

export interface SecuritySettings { totpEnabled: boolean; totpPending: boolean; totpEnabledAt: string | null; securityPasswordSet: boolean; securityPasswordChangedAt: string | null; setupAvailable: boolean }
interface Enrollment { challengeToken: string; secret: string; uri: string; expiresAt: string; replacing: boolean }
export type SecurityMode = 'enroll' | 'disable' | 'set' | 'change' | 'reset' | null

export function useAccountSecurity() {
  const { t } = useI18n(), router = useRouter(), auth = useAuth()
  const settings = ref<SecuritySettings | null>(null), mode = ref<SecurityMode>(null), pending = ref<Enrollment | null>(null)
  const busy = ref(false), error = ref(''), uncertain = ref(false), qr = ref(''), now = ref(Date.now())
  const form = reactive({ password: '', code: '', oldPassword: '', newPassword: '', confirmation: '', newCode: '' })
  let mounted = true, revision = 0, timer: ReturnType<typeof setInterval> | undefined
  const remaining = computed(() => pending.value ? Math.max(0, Math.ceil((Date.parse(pending.value.expiresAt) - now.value) / 1000)) : 0)
  const needsCode = computed(() => !!settings.value?.totpEnabled)
  const passwordMode = computed(() => mode.value === 'set' || mode.value === 'change' || mode.value === 'reset')
  function clearInputs() { Object.assign(form, { password: '', code: '', oldPassword: '', newPassword: '', confirmation: '', newCode: '' }) }
  function discard() { mode.value = null; pending.value = null; qr.value = ''; clearInputs() }
  function open(next: SecurityMode) { if (busy.value || uncertain.value) return; discard(); error.value = ''; mode.value = next }
  function close() { if (!busy.value) { discard(); error.value = '' } }
  async function reload() {
    if (busy.value || auth.user?.locked) return
    busy.value = true; error.value = ''; const current = ++revision, token = getToken()
    try {
      const result = await get<SecuritySettings>('/auth/security-settings')
      if (!mounted || revision !== current || token !== getToken()) return
      settings.value = result; uncertain.value = false; discard()
    } catch (cause) { if (mounted && current === revision) error.value = cause instanceof ApiError && cause.code === 423 ? '' : (cause as Error).message }
    finally { if (mounted && current === revision) busy.value = false }
  }
  async function signedOut() { discard(); auth.reset(); await router.replace({ path: '/login', query: { securityUpdated: '1' } }) }
  async function mutate(action: (current: () => boolean) => Promise<void>) {
    if (busy.value || uncertain.value) return
    busy.value = true; error.value = ''
    const token = getToken(), sequence = revision
    const current = () => mounted && getToken() === token && sequence === revision
    try { await action(current) }
    catch (cause) {
      if (!current()) return
      if (cause instanceof ApiError && cause.code === 401) { auth.reset(); await router.replace('/login'); return }
      uncertain.value = !(cause instanceof ApiError) || cause.code === 0 || cause.code >= 500
      error.value = uncertain.value ? t('auth.securityUncertain') : (cause as Error).message
    } finally { if (current()) { clearInputs(); busy.value = false } }
  }
  async function submit() {
    if (!settings.value || !mode.value || !form.password || (needsCode.value && !/^[0-9]{6}$/.test(form.code)) ||
      (passwordMode.value && (!validSecurityPassword(form.newPassword) || form.newPassword !== form.confirmation || (mode.value === 'change' && !form.oldPassword)))) {
      error.value = t('auth.securityInputError'); return
    }
    const action = mode.value
    const input = { password: form.password, code: needsCode.value ? form.code : undefined, oldPassword: form.oldPassword || undefined, newPassword: form.newPassword, confirmation: form.confirmation }
    await mutate(async (current) => {
      if (action === 'enroll') {
        const result = await post<Enrollment>('/auth/totp-enrollments', { password: input.password, code: input.code })
        if (!current()) return
        pending.value = result; mode.value = null
        // Generated locally; the provisioning secret never leaves this origin for QR rendering.
        const image = await QRCode.toDataURL(result.uri, { width: 220, margin: 2, errorCorrectionLevel: 'M' })
        if (current()) qr.value = image
      } else {
        if (action === 'disable') await post('/auth/totp-credentials/disable', { password: input.password, code: input.code })
        else if (action === 'set') await post('/auth/security-passwords', input)
        else if (action === 'change') await put('/auth/security-passwords', input)
        else await post('/auth/security-passwords/reset', input)
        if (current()) await signedOut()
      }
    })
  }
  async function confirm() {
    if (!pending.value || !remaining.value || !/^[0-9]{6}$/.test(form.newCode)) { error.value = t('auth.securityInputError'); return }
    const input = { challengeToken: pending.value.challengeToken, code: form.newCode }
    await mutate(async (current) => { await post('/auth/totp-enrollments/confirm', input); if (current()) await signedOut() })
  }
  async function cancel() {
    if (!pending.value) return
    const token = pending.value.challengeToken
    await mutate(async (current) => { await post('/auth/totp-enrollments/cancel', { challengeToken: token }); if (current()) { discard(); if (settings.value) settings.value.totpPending = false } })
  }
  onMounted(() => {
    void reload()
    timer = setInterval(() => {
      now.value = Date.now()
      if (pending.value && remaining.value === 0 && !busy.value) { discard(); error.value = t('auth.enrollmentExpired') }
    }, 1000)
  })
  watch(() => auth.user ? `${auth.user.id}:${auth.user.portal}` : '', () => { revision++; discard(); settings.value = null; busy.value = false; uncertain.value = false })
  watch(() => auth.user?.locked, (locked, previous) => {
    if (locked) { revision++; discard(); settings.value = null; error.value = ''; busy.value = false; uncertain.value = false }
    else if (previous === true && locked === false) void reload()
  })
  onBeforeUnmount(() => { mounted = false; revision++; clearInterval(timer); discard() })
  return { settings, mode, pending, busy, error, uncertain, qr, form, remaining, needsCode, passwordMode, reload, open, close, submit, confirm, cancel }
}
