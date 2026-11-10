<script setup lang="ts">
import type { MenuItem } from '@/stores/auth'
import { menuRoute } from '@/lib/menus'
defineProps<{ item: MenuItem }>()
</script>
<template>
  <el-sub-menu v-if="item.children?.length" :index="item.id">
    <template #title>{{ item.name }}</template>
    <MenuBranch v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>
  <li v-else-if="item.path && item.openMode === 'EXTERNAL'" class="el-menu-item" role="menuitem">
    <a :href="item.path" target="_blank" rel="noopener noreferrer">{{ item.name }}</a>
  </li>
  <el-menu-item v-else-if="item.path" :index="menuRoute(item)">{{ item.name }}</el-menu-item>
</template>
