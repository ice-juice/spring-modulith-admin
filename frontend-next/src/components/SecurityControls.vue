<script setup lang="ts">
import { validPassword } from '@/lib/password'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { LockKeyhole } from '@lucide/vue'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PasswordInput from '@/components/business/PasswordInput.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { useAuth } from '@/stores/auth'
import { post, put } from '@/api/http'
import { notify } from '@/lib/feedback'
const auth = useAuth(), router = useRouter(), { t } = useI18n(), locked = computed(() => !!auth.user?.locked)
const props = defineProps<{ beforeExit?: () => Promise<boolean> }>()
const passwordOpen = ref(false), oldPassword = ref(''), newPassword = ref(''), confirmation = ref(''), unlockPassword = ref(''), busy = ref(false), error = ref('')
function openPassword() { error.value = ''; oldPassword.value = ''; newPassword.value = ''; confirmation.value = ''; passwordOpen.value = true }
async function lock() { try { await post('/auth/lock'); if (auth.user) auth.user.locked = true } catch (cause) { notify((cause as Error).message, 'error') } }
async function unlock() { if (busy.value || !unlockPassword.value) return; busy.value = true; error.value = ''; try { await post('/auth/unlock', { password: unlockPassword.value }); unlockPassword.value = ''; if (auth.user) auth.user.locked = false } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
async function logout() { if (props.beforeExit && !await props.beforeExit()) return; await auth.logout(); await router.replace('/login') }
async function save() { if (busy.value) return; error.value = ''; if (!oldPassword.value || !validPassword(newPassword.value)) { error.value = t('iam.passwordRule'); return }; if (newPassword.value !== confirmation.value) { error.value = t('auth.passwordMismatch'); return }; if (props.beforeExit && !await props.beforeExit()) return; busy.value = true; try { await put('/iam/users/me/password', { oldPassword: oldPassword.value, newPassword: newPassword.value }); oldPassword.value = ''; newPassword.value = ''; confirmation.value = ''; passwordOpen.value = false; notify(t('auth.passwordChanged')); auth.reset(); await router.replace('/login') } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
watch(passwordOpen, open => { if (!open) { oldPassword.value = ''; newPassword.value = ''; confirmation.value = '' } })
defineExpose({ openPassword, lock })
</script>
<template><EditorDialog v-model="passwordOpen" :title="t('auth.changePassword')" :busy="busy" :dirty="!!oldPassword || !!newPassword || !!confirmation" @save="save"><ErrorNotice :message="error" /><form class="space-y-4" @submit.prevent="save"><div class="space-y-2"><Label for="old-password">{{ t('auth.oldPassword') }}</Label><PasswordInput id="old-password" v-model="oldPassword" autocomplete="current-password" /></div><div class="space-y-2"><Label for="new-password">{{ t('auth.newPassword') }}</Label><PasswordInput id="new-password" v-model="newPassword" autocomplete="new-password" /></div><div class="space-y-2"><Label for="confirm-password">{{ t('auth.confirmPassword') }}</Label><PasswordInput id="confirm-password" v-model="confirmation" autocomplete="new-password" /></div><button type="submit" hidden tabindex="-1" aria-hidden="true">{{ t('common.save') }}</button></form></EditorDialog><Dialog :open="locked"><DialogContent :show-close-button="false" class="sm:max-w-sm" @escape-key-down.prevent @pointer-down-outside.prevent @interact-outside.prevent><DialogHeader><div class="mx-auto mb-3 flex size-12 items-center justify-center rounded-xl bg-accent text-primary"><LockKeyhole class="size-6" /></div><DialogTitle class="text-center">{{ t('auth.locked') }}</DialogTitle><DialogDescription class="text-center">{{ auth.user?.name }}</DialogDescription></DialogHeader><ErrorNotice :message="error" /><form class="space-y-4" @submit.prevent="unlock"><Label for="unlock-password">{{ t('auth.password') }}</Label><PasswordInput id="unlock-password" v-model="unlockPassword" autocomplete="current-password" :disabled="busy" /><Button class="w-full" type="submit" :disabled="busy || !unlockPassword">{{ t(busy ? 'common.loading' : 'auth.unlock') }}</Button><Button class="w-full" variant="ghost" type="button" :disabled="busy" @click="logout">{{ t('common.signOut') }}</Button></form></DialogContent></Dialog></template>
