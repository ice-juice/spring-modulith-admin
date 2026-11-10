<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import { findMenu } from '@/lib/menus'
const route = useRoute()
const { t } = useI18n()
const auth = useAuth()
const menu = computed(() =>
  findMenu(
    auth.user?.menus || [],
    (node) => node.id === route.params.id && node.openMode === 'IFRAME',
  ),
)
</script>
<template>
  <iframe
    v-if="menu?.path"
    :src="menu.path"
    :title="menu.name"
    class="embedded-page"
    sandbox="allow-scripts allow-same-origin allow-forms"
    referrerpolicy="no-referrer"
  />
  <el-empty v-else :description="t('common.forbidden')" />
</template>
