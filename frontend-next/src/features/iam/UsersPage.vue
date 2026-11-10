<script setup lang="ts">
import { validPassword } from '@/lib/password'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { notify } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import ResourcePage from './ResourcePage.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import type { Row } from './resources'
const { t } = useI18n(), auth = useAuth(), resetOpen = ref(false), target = ref<Row>(), password = ref(''), busy = ref(false), error = ref('')
function editPassword(row: Row) { target.value = row; password.value = ''; error.value = ''; resetOpen.value = true }
async function save() { if (busy.value || !target.value) return; if (!validPassword(password.value)) { error.value = t('iam.passwordRule'); return }; busy.value = true; try { await put('/iam/users/' + target.value.id + '/password', { password: password.value }); password.value = ''; resetOpen.value = false; notify(t('common.saved')) } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
</script>
<template><ResourcePage resource-id="users"><template #row-actions="{ row }"><Button v-if="!row.superAdmin && row.id !== auth.user?.id" v-permission="'iam:user:reset-password'" variant="ghost" size="sm" @click="editPassword(row)">{{ t('iam.resetPassword') }}</Button></template></ResourcePage><EditorDialog v-model="resetOpen" :title="t('iam.resetPassword')" :busy="busy" :dirty="!!password" @save="save"><ErrorNotice :message="error" /><div class="space-y-2"><Label for="reset-password">{{ t('auth.newPassword') }}</Label><PasswordInput id="reset-password" v-model="password" autocomplete="new-password" :placeholder="t('iam.passwordRule')" @keyup.enter="save" /></div></EditorDialog></template>
