<script setup lang="ts">
import { computed } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { i18n } from '@/locales'
const elementLocale = computed(() => (i18n.global.locale.value === 'zh-CN' ? zhCn : en))
</script>
<template>
  <el-config-provider :locale="elementLocale"><RouterView /></el-config-provider>
</template>
