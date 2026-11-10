<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
const { t } = useI18n()
const route = useRoute()
</script>
<template>
  <el-result
    :icon="route.path === '/403' ? 'warning' : 'info'"
    :title="t(route.path === '/403' ? 'common.forbidden' : 'common.notFound')"
    :sub-title="route.path === '/403' ? t('common.forbiddenHint') : ''"
  >
    <template #extra
      ><el-button type="primary" @click="$router.push('/')">{{
        t('common.backHome')
      }}</el-button></template
    >
  </el-result>
</template>
