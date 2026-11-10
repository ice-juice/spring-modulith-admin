<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import ResourcePage from './ResourcePage.vue'
import RoleGrantDialog from './RoleGrantDialog.vue'
import type { Row } from './resources'
const { t } = useI18n(), router = useRouter(), role = ref<Row>(), grantOpen = ref(false)
function grant(row: Row) { role.value = row; grantOpen.value = true }
</script>
<template><ResourcePage resource-id="roles"><template #row-actions="{ row }"><Button v-permission="'iam:role:grant'" variant="ghost" size="sm" @click="grant(row)">{{ t('iam.grant') }}</Button><Button v-permission="'iam:user:view'" variant="ghost" size="sm" @click="router.push({ path: '/iam/users', query: { roleId: row.id, roleName: String(row.name) } })">{{ t('iam.viewMembers') }}</Button></template></ResourcePage><RoleGrantDialog v-model="grantOpen" :role="role" /></template>
