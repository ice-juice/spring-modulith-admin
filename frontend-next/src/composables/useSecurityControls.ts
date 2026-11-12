import { computed, onScopeDispose, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ApiError, get, post, put } from '@/api/http'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { credentialOutcomeUnknown, useCredentialIntents, useSessionLockIntents, type CredentialTicket } from '@/stores/credential-intents'
import { getToken } from '@/lib/token'
import { validPassword } from '@/lib/password'
import { localFailure, operationReceipt } from '@/lib/operation-result'
import { notify } from '@/lib/feedback'

// RequestGovernanceConfiguration binds operation IDs to group + SHA256(method + resolved route)[0:24].
const passwordOperation = 'iam.users.9721c28fc095e11b25656509'
export function useSecurityControls(beforeExit: () => Promise<boolean>) {
  const auth = useAuth(), router = useRouter(), { t } = useI18n(), credentials = useCredentialIntents(), locks = useSessionLockIntents()
  const passwordOpen = ref(false), oldPassword = ref(''), newPassword = ref(''), confirmation = ref(''), unlockPassword = ref('')
  const passwordWork = ref(false), sessionWork = ref(false), exiting = ref(false), querying = ref(false), privacy = ref(false)
  const localShieldPending = ref(false)
  const message = ref(''); let live = true, revision = 0, formRevision = 0
  const passwordBusy = computed(() => passwordWork.value || credentials.busy || querying.value || exiting.value)
  const passwordUnknown = computed(() => credentials.unresolved || credentials.unavailable)
  const canCheckPassword = computed(() => credentials.unresolved && !credentials.unavailable)
  const lockUnknown = computed(() => locks.unresolved || locks.unavailable || localShieldPending.value)
  const sessionBusy = computed(() => sessionWork.value || locks.busy || !locks.ready)
  const locked = computed(() => privacy.value || !!auth.user && (!locks.ready && locks.recovering || !!auth.user.locked || lockUnknown.value))
  const error = computed(() => locks.problem || credentials.problem || message.value || (locked.value && lockUnknown.value && !sessionWork.value ? t('auth.lockUncertain')
    : !locked.value && passwordUnknown.value && !passwordBusy.value ? t('auth.securityUncertain') : ''))
  const identity = () => auth.user ? `${auth.user.id}:${auth.user.portal}:${getToken()}` : ''
  function guard() { const owner = identity(), version = revision; return () => live && !!owner && version === revision && owner === identity() }
  function clearPasswords() { oldPassword.value = ''; newPassword.value = ''; confirmation.value = ''; unlockPassword.value = '' }
  function openPassword() {
    if (locked.value || passwordBusy.value || !auth.user) return
    clearPasswords(); message.value = ''; passwordOpen.value = true
  }
  async function leaveAfterPassword() {
    passwordOpen.value = false; clearPasswords(); privacy.value = true; notify(t('auth.passwordChanged')); auth.reset(); await router.replace('/login')
  }
  async function save() {
    if (passwordBusy.value || passwordUnknown.value || locked.value || !auth.user) return
    message.value = ''
    if (!oldPassword.value || !validPassword(newPassword.value)) { message.value = t('iam.passwordRule'); return }
    if (newPassword.value !== confirmation.value) { message.value = t('auth.passwordMismatch'); return }
    const input = { oldPassword: oldPassword.value, newPassword: newPassword.value }, owner = guard(), version = revision, formVersion = formRevision
    const current = () => owner() && formVersion === formRevision && !locked.value
    passwordWork.value = true
    let ticket: CredentialTicket | null = null
    try {
      ticket = await credentials.begin()
      if (!ticket || !current()) return
      if (!await beforeExit() || !current() || locked.value) return
      const recorded = await credentials.sent(ticket)
      if (!current()) return
      if (!recorded) { message.value = credentials.problem || t('common.operation.storage'); return }
      try {
        await put('/iam/users/me/password', input, { expectedIdentity: ticket.token, idempotencyKey: ticket.key })
        credentials.settle(ticket, true)
        if (current()) await leaveAfterPassword()
      } catch (cause) {
        const outcome = localFailure(cause)
        credentials.settle(ticket, ['completed', 'failed', 'rejected'].includes(outcome))
        if (!current()) return
        if (outcome === 'completed') await leaveAfterPassword()
        else message.value = passwordUnknown.value ? t('auth.securityUncertain') : (cause as Error).message
      }
    } catch (cause) { if (current()) message.value = (cause as Error).message }
    finally {
      if (ticket) await credentials.release(ticket)
      if (version === revision) { clearPasswords(); passwordWork.value = false }
    }
  }
  async function checkPassword() {
    if (passwordBusy.value || locked.value) return
    const key = credentials.pendingKey(), token = getToken(), current = guard(), version = revision
    if (!key || !token) return
    querying.value = true; message.value = ''
    try {
      const receipt = await credentials.check(async (originalKey, originalToken) => operationReceipt(await post('/common/requests/lookup', { key: originalKey }, { expectedIdentity: originalToken })),
        value => !!value && value.operation === passwordOperation && ['SUCCEEDED', 'FAILED_FINAL'].includes(value.state))
      if (!current()) return
      if (!receipt || receipt.operation !== passwordOperation || !['SUCCEEDED', 'FAILED_FINAL'].includes(receipt.state)) {
        message.value = t('auth.securityUncertain'); return
      }
      if (!credentials.unresolved && !credentials.unavailable) {
        if (receipt.state === 'SUCCEEDED') await leaveAfterPassword()
        else { clearPasswords(); message.value = t('common.operation.failed') }
      }
    } catch (cause) { if (current()) message.value = cause instanceof ApiError && cause.code === 404 ? t('common.operation.notFound') : (cause as Error).message }
    finally { if (version === revision) querying.value = false }
  }
  async function lock() {
    if (locked.value || sessionBusy.value || exiting.value || !auth.user) return
    const current = guard(), version = revision
    localShieldPending.value = true; privacy.value = true; auth.user.locked = true; clearPasswords(); passwordOpen.value = false
    sessionWork.value = true; message.value = ''
    let ticket: CredentialTicket | null = null
    try {
      ticket = await locks.begin('LOCK')
      if (!current()) return
      const recorded = ticket ? await locks.sent(ticket) : false
      if (!current()) return
      if (!ticket || !recorded) {
        if (!locks.unavailable && (locks.status?.state === 'LOCKED' || locks.unresolved && locks.status?.action === 'LOCK')) { localShieldPending.value = false; return }
        // Without durable local shielding metadata, keep no usable local session across a reload.
        auth.reset(); await router.replace('/login'); return
      }
      await post('/auth/lock', undefined, { expectedIdentity: ticket.token, idempotencyKey: ticket.key })
      if (locks.settle(ticket, 'success')) localShieldPending.value = false
    } catch { if (current()) message.value = t('auth.lockUncertain') }
    finally { if (ticket) await locks.release(ticket); if (version === revision) sessionWork.value = false }
  }
  async function unlock() {
    if (sessionBusy.value || lockUnknown.value || exiting.value || !locked.value || !unlockPassword.value) return
    const password = unlockPassword.value, current = guard(), version = revision
    sessionWork.value = true; message.value = ''
    let ticket: CredentialTicket | null = null
    try {
      ticket = await locks.begin('UNLOCK')
      if (!ticket || !current() || !await locks.sent(ticket) || !current()) return
      try {
        await post('/auth/unlock', { password }, { expectedIdentity: ticket.token, idempotencyKey: ticket.key })
        const acknowledged = locks.settle(ticket, 'success')
        if (current() && acknowledged && !locks.unavailable) { privacy.value = false; if (auth.user) auth.user.locked = false }
      } catch (cause) {
        if (!credentialOutcomeUnknown(cause)) locks.settle(ticket, 'rejected')
        if (current()) message.value = lockUnknown.value ? t('auth.lockUncertain') : (cause as Error).message
      }
    } catch (cause) { if (current()) message.value = (cause as Error).message }
    finally { if (ticket) await locks.release(ticket); if (version === revision) { unlockPassword.value = ''; sessionWork.value = false } }
  }
  async function logout() {
    if (exiting.value || !auth.user) return
    const current = guard(), version = revision
    exiting.value = true
    try {
      let left = false
      try { left = await auth.logout(async () => {
        if (!await beforeExit() || !current()) return false
        clearPasswords(); passwordOpen.value = false; privacy.value = true
        return true
      }) }
      catch { left = !getToken() && !auth.user && !auth.authenticating }
      if (left && !getToken() && !auth.user && !auth.authenticating) await router.replace('/login')
    } catch (cause) { if (current()) message.value = (cause as Error).message }
    finally { if (version === revision) exiting.value = false }
  }
  watch(identity, (_value, previous) => {
    revision++; clearPasswords(); passwordOpen.value = false; message.value = ''; passwordWork.value = false; sessionWork.value = false; exiting.value = false; querying.value = false
    localShieldPending.value = false
    if (auth.user) privacy.value = !!auth.user.locked
    else if (previous) privacy.value = true // Keep private pixels covered while the login route is loading.
  }, { flush: 'sync' })
  watch(() => auth.user?.locked, value => { if (value) { formRevision++; clearPasswords(); passwordOpen.value = false } }, { flush: 'sync' })
  watch([() => locks.unresolved, () => locks.status?.state, () => auth.user?.locked], ([unknown, state]) => {
    if ((unknown || state === 'LOCKED') && auth.user && !auth.user.locked) { privacy.value = true; auth.user.locked = true }
  }, { flush: 'sync' })
  watch(() => locks.peerUnlockKey, async key => {
    // Initial historical state cannot override a newer server-side lock. Only a newly observed
    // acknowledgement may trigger an authorized read; the original key must still be current after it.
    if (!key || sessionWork.value || localShieldPending.value || exiting.value || !auth.user) return
    const current = guard(), token = getToken()
    if (!token) return
    try {
      const server = await get<CurrentUser>('/auth/me', undefined, { expectedIdentity: token })
      if (!current() || sessionWork.value || localShieldPending.value || exiting.value || !locks.confirmedUnlock(key) || server.locked !== false
        || server.id !== auth.user?.id || server.portal !== auth.user.portal) return
      privacy.value = false; auth.user.locked = false; clearPasswords(); message.value = ''
    } catch { /* No authoritative current unlocked view: preserve the local privacy shield. */ }
  }, { flush: 'sync' })
  watch(passwordOpen, value => { if (!value) { formRevision++; oldPassword.value = ''; newPassword.value = ''; confirmation.value = '' } }, { flush: 'sync' })
  onScopeDispose(() => { live = false; revision++; clearPasswords() })
  return { auth, passwordOpen, oldPassword, newPassword, confirmation, unlockPassword, passwordBusy, passwordUnknown, canCheckPassword,
    sessionBusy, lockUnknown, locked, exiting, querying, error, openPassword, save, checkPassword, lock, unlock, logout }
}
