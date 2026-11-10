<script setup lang="ts">
import type { MenuItem } from '@/stores/auth'
import { menuRoute } from '@/lib/menus'
import MenuIcon from './MenuIcon.vue'
defineProps<{ item: MenuItem }>()
</script>
<template>
  <el-sub-menu v-if="item.children?.length" :index="item.id" :aria-label="item.name">
    <template #title
      ><MenuIcon :name="item.icon" directory /><span>{{ item.name }}</span></template
    >
    <MenuBranch v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>
  <li v-else-if="item.path && item.openMode === 'EXTERNAL'" class="el-menu-item" role="menuitem">
    <a
      :href="item.path"
      :aria-label="item.name"
      :title="item.name"
      target="_blank"
      rel="noopener noreferrer"
      ><MenuIcon :name="item.icon" /><span class="external-menu-label">{{ item.name }}</span></a
    >
  </li>
  <el-menu-item v-else-if="item.path" :index="menuRoute(item)" :aria-label="item.name"
    ><MenuIcon :name="item.icon" /><template #title>{{ item.name }}</template></el-menu-item
  >
</template>
