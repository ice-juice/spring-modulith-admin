import { onMounted, onUnmounted, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { confirmAction, notify } from '@/lib/feedback'
export function useUnsavedChanges(dirty: Ref<boolean>, busy?: Ref<boolean>) {
  const { t } = useI18n()
  onBeforeRouteLeave(async () => { if (busy?.value) { notify(t('common.operationInProgress'), 'info'); return false }; return !dirty.value || await confirmAction(t('common.unsavedConfirm'), t('common.confirm')) })
  const beforeUnload = (event: BeforeUnloadEvent) => { if (dirty.value || busy?.value) { event.preventDefault(); event.returnValue = '' } }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
}
