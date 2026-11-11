<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
<template>
  <div class="table-workspace">
    <div v-if="$slots.tools" class="table-workspace-tools outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring/50" role="region" :aria-label="t('common.tableTools')" tabindex="0"><slot name="tools" /></div>
    <div class="table-workspace-results"><slot /></div>
    <div v-if="$slots.footer" class="table-workspace-footer shrink-0"><slot name="footer" /></div>
  </div>
</template>
