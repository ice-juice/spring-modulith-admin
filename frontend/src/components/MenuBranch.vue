<script setup lang="ts">
import type { MenuItem } from '@/stores/auth'
defineProps<{ item: MenuItem }>()
</script>
<template>
  <el-sub-menu v-if="item.children?.length" :index="item.id">
    <template #title>{{ item.name }}</template>
    <MenuBranch v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>
  <el-menu-item v-else-if="item.path" :index="item.path">{{ item.name }}</el-menu-item>
</template>
