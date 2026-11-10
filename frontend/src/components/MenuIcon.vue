<script setup lang="ts">
import { computed, type Component } from 'vue'
import {
  Bell,
  Connection,
  Document,
  Folder,
  HomeFilled,
  Lock,
  Monitor,
  Setting,
  User,
} from '@element-plus/icons-vue'
const props = defineProps<{ name?: string | null; directory?: boolean }>()
const icons: Record<string, Component> = {
  Bell,
  Connection,
  Document,
  Folder,
  HomeFilled,
  Lock,
  Monitor,
  Setting,
  User,
}
const icon = computed(() => icons[props.name || ''] || (props.directory ? Folder : Document))
</script>
<template>
  <el-icon aria-hidden="true"><component :is="icon" /></el-icon>
</template>
