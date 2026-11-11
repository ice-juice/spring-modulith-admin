<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import { findMenu } from '@/lib/menus'
import { safeBrowserDestination } from '@/lib/web-addresses'
import EmptyState from '@/components/business/EmptyState.vue'
const route = useRoute(), auth = useAuth(), { t } = useI18n()
const menu = computed(() => findMenu(auth.user?.menus || [], node => node.id === route.params.id && node.openMode === 'IFRAME'))
const safe = computed(() => safeBrowserDestination(menu.value?.path))
</script>
<template><iframe v-if="safe" :src="safe" :title="menu?.name" class="min-h-[70dvh] w-full rounded-2xl border bg-card shadow-[var(--shadow-surface)]" sandbox="allow-scripts allow-same-origin allow-forms" referrerpolicy="no-referrer" /><EmptyState v-else :title="t('common.forbidden')" /></template>
