<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { Users, ShieldCheck } from '@lucide/vue'
import { validPassword } from '@/lib/password'
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { notify } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Label } from '@/components/ui/label'
import ResourcePage from './ResourcePage.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import type { Row } from './resources'
const { t } = useI18n(), auth = useAuth(), resetOpen = ref(false), target = ref<Row>(), password = ref(''), action = useSubmission(), busy = action.busy, error = action.error
function editPassword(row: Row) { action.resetView(); action.bind('iam.users.' + row.id); target.value = row; password.value = ''; error.value = ''; resetOpen.value = true }
async function save() {
  if (busy.value || !target.value) return
  const id = target.value.id, input = { password: password.value }
  const result = await action.run({ operation: 'users.password', resource: 'iam.users.' + id, protocol: 'LOCAL' }, () => {
    if (!validPassword(input.password)) { error.value = t('iam.passwordRule'); return }
    return options => put('/iam/users/' + id + '/password', input, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') { password.value = ''; resetOpen.value = false; notify(t('common.saved')) }
}
async function check() { const operation = action.record.value?.operation; if (await action.check() === 'SUCCEEDED') { if (operation === 'users.password') { password.value = ''; resetOpen.value = false; notify(t('common.operation.completed')) } else notify(t('common.operation.previousFinished'), 'info') } }
watch(resetOpen, open => { if (!open) password.value = '' })
watch(() => auth.user?.locked, locked => { if (locked) { password.value = ''; resetOpen.value = false } })
</script>
<template><ResourcePage resource-id="users" :eyebrow="t('iam.userEyebrow')" :description="t('iam.userHint')"><template #overview="{ rows, total, loading }"><section class="softlight-hero mb-6 flex flex-wrap items-center gap-6 p-5 sm:p-6" :aria-busy="loading"><span class="grid size-12 shrink-0 place-items-center rounded-2xl border border-primary/10 bg-card/70 text-primary"><Users class="size-6" /></span><div class="min-w-0 flex-1"><h2 class="text-base font-semibold">{{ t('iam.peopleTitle') }}</h2><p class="mt-1 text-xs leading-5 text-muted-foreground">{{ t('iam.peopleHint') }}</p></div><div class="flex flex-wrap gap-8"><div><p class="text-2xl font-semibold tabular-nums">{{ total }}</p><p class="mt-1 text-xs text-muted-foreground">{{ t('common.total', { total }) }}</p></div><div><p class="flex items-center gap-2 text-2xl font-semibold tabular-nums">{{ rows.filter((row: Row) => row.enabled).length }}<ShieldCheck class="size-4 text-[var(--success)]" /></p><p class="mt-1 text-xs text-muted-foreground">{{ t('iam.pageEnabled') }}</p></div></div></section></template><template #row-actions="{ row }"><PermissionButton v-if="!row.superAdmin && row.id !== auth.user?.id" :permission="'iam:user:reset-password'" variant="ghost" size="sm" @click="editPassword(row)">{{ t('iam.resetPassword') }}</PermissionButton></template></ResourcePage><EditorDialog v-model="resetOpen" :title="t('iam.resetPassword')" :busy="busy" :submit-disabled="action.unresolved.value" :dirty="!!password" @save="save"><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><div class="space-y-2"><Label for="reset-password">{{ t('auth.newPassword') }}</Label><PasswordInput id="reset-password" :disabled="busy || action.unresolved.value" v-model="password" autocomplete="new-password" :placeholder="t('iam.passwordRule')" @keyup.enter="save" /></div></EditorDialog></template>
