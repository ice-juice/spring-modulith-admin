import { onMounted, onUnmounted, type Ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { confirmAction, notify } from '@/lib/feedback'
import { useAuth } from '@/stores/auth'
export function useUnsavedChanges(dirty: Ref<boolean>, busy?: Ref<boolean>) {
  const { t } = useI18n()
  const auth = useAuth(), owner = auth.user?.id
  const guard = async () => {
    // Expiry or a forced logout must not be held on a protected page by an old draft.
    if (owner && auth.user?.id !== owner) return true
    if (busy?.value) { notify(t('common.operationInProgress'), 'info'); return false }
    return !dirty.value || await confirmAction(t('common.unsavedConfirm'), t('common.confirm'))
  }
  onBeforeRouteLeave(guard)
  onBeforeRouteUpdate(guard)
  const beforeUnload = (event: BeforeUnloadEvent) => { if (dirty.value || busy?.value) { event.preventDefault(); event.returnValue = '' } }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
}
