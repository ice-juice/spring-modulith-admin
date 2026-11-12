<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PermissionButton from '@/components/business/PermissionButton.vue'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { usePreferences } from '@/stores/preferences'
import { useSessionRevocations } from '@/composables/useSessionRevocations'
import { formatInstant } from '@/lib/time'
const { t, locale } = useI18n(), preferences = usePreferences()
const { rows, keyword, page, total, error, request, unresolved, slow, blocked, load, refresh, search, revoke } = useSessionRevocations()
const columns = computed(() => [{key:'username',title:t('auth.username')},{key:'name',title:t('common.name')},{key:'ip',title:t('auth.ip')},{key:'userAgent',title:t('auth.device'),width:240},{key:'lastSeenAt',title:t('auth.lastSeen'),width:190},{key:'locked',title:t('common.status')},{key:'actions',title:t('common.actions'),width:150}])
</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('auth.sessions')"><Button variant="outline" :disabled="request.loading.value" @click="refresh">{{ t('common.refresh') }}</Button></PageHeader><FilterBar :busy="request.loading.value" @search="search"><Input v-model="keyword" :placeholder="t('auth.username')" :aria-label="t('auth.username')" /></FilterBar><ErrorNotice :message="request.error.value || error" /><OperationStatus :slow="slow" /><div v-if="unresolved" role="status" aria-live="polite" class="mb-3 rounded-lg border p-3 text-sm">{{ t('auth.sessionRevokeUnknown') }}</div></template><DataTable :rows="rows" :columns="columns" :loading="request.loading.value" :page="page" :total="total" @update:page="page = $event; load()"><template #lastSeenAt="{ row }">{{ formatInstant(row.lastSeenAt, preferences.timeZone, locale) }}</template><template #locked="{ row }"><StatusBadge :label="t(row.locked ? 'auth.locked' : 'auth.online')" :tone="row.locked ? 'warning' : 'success'" /></template><template #actions="{ row }"><StatusBadge v-if="row.current" :label="t('auth.currentSession')" /><PermissionButton :disabled="blocked(row.id)" v-else :permission="'auth:session:revoke'" variant="ghost" size="sm" class="text-destructive" @click="revoke(row)">{{ t('auth.forceLogout') }}</PermissionButton></template></DataTable></TableWorkspace></template>
