<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { notify } from '@/lib/feedback'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import UploadButton from '@/components/business/UploadButton.vue'
import AvatarImage from '@/components/AvatarImage.vue'
const { t } = useI18n(), auth = useAuth(), form = reactive({ id: '', username: '', name: '', email: null as string | null, mobile: null as string | null, gender: null as string | null, avatarFileId: null as string | null }), ready = ref(false), action = useSubmission(), syncing = ref(false), busy = computed(() => action.busy.value || syncing.value), error = action.error, original = ref('')
action.bind('iam.profile.self')
const dirty = computed(() => ready.value && JSON.stringify(form) !== original.value)
useUnsavedChanges(dirty)
const genders = computed(() => ['MALE', 'FEMALE', 'UNSPECIFIED'].map(value => ({ value, label: t('iam.choices.' + value) })))
async function synchronize() {
  syncing.value = true
  try { const current = action.guard(), value = await get<typeof form>('/iam/profile'); if (!current()) return; Object.assign(form, value); original.value = JSON.stringify(form); await auth.load(false); if (current()) notify(t('common.saved')) }
  catch (cause) { error.value = (cause as Error).message } finally { syncing.value = false }
}
async function save() {
  if (busy.value || !ready.value) return
  const result = await action.run({ operation: 'profile.save', resource: 'iam.profile.self', protocol: 'LOCAL' }, () => {
    if (!form.name.trim()) { error.value = t('iam.profileNameRequired'); return }
    const input = { name: form.name, email: form.email || null, mobile: form.mobile || null, gender: form.gender || null, avatarFileId: form.avatarFileId }
    return options => put('/iam/profile', input, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') await synchronize()
}
async function check() { if (await action.check() === 'SUCCEEDED') await synchronize() }
onMounted(async () => { try { Object.assign(form, await get('/iam/profile')); original.value = JSON.stringify(form); ready.value = true } catch (cause) { error.value = (cause as Error).message } })
</script>
<template><PageHeader :title="t('common.profile')" /><Card class="max-w-4xl shadow-none"><CardContent><ErrorNotice :message="error" /><OperationStatus :slow="action.slow.value" :unresolved="action.unresolved.value" :querying="action.querying.value" queryable @check="check" /><form class="space-y-6" @submit.prevent="save"><fieldset class="contents" :disabled="busy || action.unresolved.value"><div class="profile-identity flex flex-wrap items-center gap-5 rounded-xl border p-5"><AvatarImage :file-id="form.avatarFileId" :size="72" /><div class="flex flex-wrap gap-2"><UploadButton v-permission="'file:file:upload'" :label="t('iam.uploadAvatar')" image-only :disabled="!ready || busy" @uploaded="form.avatarFileId = $event.id" @error="error = $event" /><Button type="button" variant="ghost" :disabled="!form.avatarFileId || busy" @click="form.avatarFileId = null">{{ t('iam.removeAvatar') }}</Button></div></div><div class="grid gap-5 sm:grid-cols-2"><div class="space-y-2"><Label for="profile-username">{{ t('iam.fields.username') }}</Label><Input id="profile-username" v-model="form.username" disabled autocomplete="username" /></div><div class="space-y-2"><Label for="profile-name">{{ t('common.name') }}</Label><Input id="profile-name" v-model="form.name" :disabled="!ready || busy" maxlength="128" required /></div><template v-if="auth.user?.features?.extendedProfileEnabled !== false"><div class="space-y-2"><Label for="profile-email">{{ t('iam.fields.email') }}</Label><Input id="profile-email" :model-value="form.email || ''" type="email" :disabled="!ready || busy" @update:model-value="form.email = String($event)" /></div><div class="space-y-2"><Label for="profile-mobile">{{ t('iam.fields.mobile') }}</Label><Input id="profile-mobile" :model-value="form.mobile || ''" type="tel" :disabled="!ready || busy" @update:model-value="form.mobile = String($event)" /></div><div class="space-y-2"><Label for="profile-gender">{{ t('iam.fields.gender') }}</Label><EntitySelect id="profile-gender" :model-value="form.gender" :options="genders" :disabled="!ready || busy" @update:model-value="form.gender = typeof $event === 'string' ? $event : null" /></div></template></div><div class="border-t pt-5"><Button type="submit" :disabled="!ready || busy">{{ t(busy ? 'common.loading' : 'common.save') }}</Button></div></fieldset></form></CardContent></Card></template>
