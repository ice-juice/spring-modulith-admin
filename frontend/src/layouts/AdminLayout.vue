<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import DisplaySettings from '@/components/DisplaySettings.vue'
import MenuBranch from '@/components/MenuBranch.vue'
const { t } = useI18n()
const auth = useAuth()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const title = computed(() => t(String(route.meta.titleKey || 'common.home')))
async function logout() {
  await auth.logout()
  await router.replace('/login')
}
</script>
<template>
  <div class="admin-shell" :class="{ collapsed }">
    <aside class="sidebar">
      <RouterLink class="brand" to="/"
        ><b>{{ t('common.shortBrand') }}</b
        ><span>{{ t('common.workspace') }}</span></RouterLink
      >
      <el-menu
        router
        :default-active="route.path"
        :collapse="collapsed"
        background-color="transparent"
        text-color="#b9c6d6"
        active-text-color="#6ee7c9"
      >
        <el-menu-item index="/"
          ><span>{{ t('common.home') }}</span></el-menu-item
        >
        <MenuBranch v-for="item in auth.user?.menus || []" :key="item.id" :item="item" />
      </el-menu>
      <div class="sidebar-footer">{{ t('common.brand') }}</div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="page-heading">
          <el-button
            text
            :aria-label="t(collapsed ? 'common.expand' : 'common.collapse')"
            @click="collapsed = !collapsed"
            ><span aria-hidden="true">☰</span></el-button
          ><strong>{{ title }}</strong>
        </div>
        <div class="topbar-actions">
          <DisplaySettings /><span class="user-name">{{ auth.user?.name }}</span
          ><el-button @click="logout">{{ t('common.signOut') }}</el-button>
        </div>
      </header>
      <main class="main-content"><RouterView /></main>
    </div>
  </div>
</template>
