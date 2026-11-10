<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { loadLocaleModule } from '@/locales'
import { filterMenus, findMenu } from '@/lib/menus'
import DisplaySettings from '@/components/DisplaySettings.vue'
import SecurityControls from '@/components/SecurityControls.vue'
import MenuBranch from '@/components/MenuBranch.vue'
const { t } = useI18n()
const auth = useAuth()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const menuSearch = ref('')
const activeNav = ref('')
interface Tab {
  path: string
  titleKey?: string
  fallback: string
}
const tabs = ref<Tab[]>([{ path: '/', titleKey: 'common.home', fallback: '' }])
const title = computed(() =>
  route.meta.titleKey
    ? t(String(route.meta.titleKey))
    : findMenu(auth.user?.menus || [], (menu) => menu.path === route.path)?.name ||
      t('common.home'),
)
const menus = computed(() => {
  const navigation = auth.user?.navigation.find((item) => item.id === activeNav.value)
  const roots = auth.user?.menus || []
  return filterMenus(
    navigation ? roots.filter((menu) => navigation.menuIds.includes(menu.id)) : roots,
    menuSearch.value,
  )
})
watch(
  () => route.fullPath,
  (path) => {
    if (!tabs.value.some((tab) => tab.path === path) && route.path !== '/403')
      tabs.value.push({
        path,
        titleKey: typeof route.meta.titleKey === 'string' ? route.meta.titleKey : undefined,
        fallback: title.value,
      })
  },
  { immediate: true },
)
function closeTab(path: string) {
  const index = tabs.value.findIndex((tab) => tab.path === path)
  tabs.value = tabs.value.filter((tab) => tab.path !== path)
  if (route.fullPath === path) void router.push(tabs.value[Math.max(0, index - 1)]?.path || '/')
}
async function logout() {
  await auth.logout()
  await router.replace('/login')
}
void loadLocaleModule('auth')
</script>
<template>
  <div class="admin-shell" :class="{ collapsed }">
    <aside class="sidebar">
      <RouterLink class="brand" to="/"
        ><b>{{ t('common.shortBrand') }}</b
        ><span>{{ t('common.workspace') }}</span></RouterLink
      >
      <div v-if="!collapsed" class="menu-search">
        <el-input v-model="menuSearch" :placeholder="t('common.search')" clearable />
      </div>
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
        <MenuBranch v-for="item in menus" :key="item.id" :item="item" />
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
          ><SecurityControls /><el-button @click="logout">{{ t('common.signOut') }}</el-button>
        </div>
      </header>
      <div v-if="auth.user?.navigation.length" class="portal-navigation">
        <el-button :type="activeNav === '' ? 'primary' : 'default'" text @click="activeNav = ''">{{
          t('common.all')
        }}</el-button>
        <el-button
          v-for="item in auth.user.navigation"
          :key="item.id"
          :type="activeNav === item.id ? 'primary' : 'default'"
          text
          @click="activeNav = item.id"
          >{{ item.name }}</el-button
        >
      </div>
      <div class="workspace-tabs">
        <el-tag
          v-for="tab in tabs"
          :key="tab.path"
          :closable="tab.path !== '/'"
          :type="route.fullPath === tab.path ? 'primary' : 'info'"
          @click="router.push(tab.path)"
          @close="closeTab(tab.path)"
          >{{ tab.titleKey ? t(tab.titleKey) : tab.fallback }}</el-tag
        >
      </div>
      <el-alert
        v-if="auth.user?.passwordChangeSuggested"
        :title="t('auth.changeSuggested')"
        type="warning"
        :closable="false"
      />
      <main class="main-content"><RouterView :key="route.path" /></main>
    </div>
  </div>
</template>
