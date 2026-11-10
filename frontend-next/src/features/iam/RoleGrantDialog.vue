<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { notify } from '@/lib/feedback'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PermissionTree from '@/components/business/PermissionTree.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import type { TreeNode } from '@/components/business/tree'
import type { Row } from './resources'
const props = defineProps<{ role?: Row }>(), open = defineModel<boolean>({ required: true }), { t } = useI18n(), auth = useAuth()
const nodes = ref<TreeNode[]>([]), selected = ref<string[]>([]), original = ref(''), busy = ref(false), saving = ref(false), error = ref(''), loaded = ref(false)
let generation = 0
watch(open, async value => { if (!value || !props.role) { generation++; return }; const current = ++generation; busy.value = true; loaded.value = false; error.value = ''; nodes.value = []; selected.value = []; try { const result = await Promise.all([get<TreeNode[]>('/iam/permissions/tree'), get<string[]>('/iam/roles/' + props.role.id + '/permissions')]); if (generation === current) { loaded.value = true; nodes.value = result[0]; selected.value = result[1]; original.value = JSON.stringify(result[1]) } } catch (cause) { if (generation === current) error.value = (cause as Error).message } finally { if (generation === current) busy.value = false } })
async function save() { if (busy.value || saving.value || !props.role || !loaded.value) return; saving.value = true; try { await put('/iam/roles/' + props.role.id + '/permissions', { permissionIds: [...new Set(selected.value)] }); original.value = JSON.stringify(selected.value); open.value = false; notify(t('common.saved')); await auth.load(false) } catch (cause) { error.value = (cause as Error).message } finally { saving.value = false } }
</script>
<template><EditorDialog v-model="open" :title="t('iam.grant') + ' · ' + (role?.name || '')" :busy="busy || saving" :dirty="JSON.stringify(selected) !== original" wide @save="save"><ErrorNotice :message="error" /><PermissionTree v-model="selected" :nodes="nodes" :loading="busy" /></EditorDialog></template>
