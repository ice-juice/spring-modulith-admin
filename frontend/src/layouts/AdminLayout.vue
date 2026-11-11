<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { useBranding } from '@/stores/branding'
import { filterMenus, findMenu } from '@/lib/menus'
import { trackPageTab, type WorkspaceTab } from '@/lib/tabs'
import DisplaySettings from '@/components/DisplaySettings.vue'
import SecurityControls from '@/components/SecurityControls.vue'
import MenuBranch from '@/components/MenuBranch.vue'
import MenuIcon from '@/components/MenuIcon.vue'
import BrandingFooter from '@/components/BrandingFooter.vue'
import AvatarImage from '@/components/AvatarImage.vue'
import { useMessages } from '@/stores/messages'
import { Bell } from '@element-plus/icons-vue'
const { t, te } = useI18n()
const auth = useAuth()
const messages = useMessages()
onMounted(messages.start)
onUnmounted(messages.stop)
watch(
  () => auth.user?.locked,
  (locked) => {
    if (locked) messages.pause()
    else void messages.connect()
  },
)
const preferences = usePreferences(),
  branding = useBranding()
const route = useRoute()
const router = useRouter()
const compactMedia = window.matchMedia('(max-width: 760px)')
const compact = ref(compactMedia.matches)
const collapsed = ref(compact.value)
function resizeNavigation(event: MediaQueryListEvent) {
  compact.value = event.matches
  collapsed.value = event.matches
}
onMounted(() => compactMedia.addEventListener('change', resizeNavigation))
onUnmounted(() => compactMedia.removeEventListener('change', resizeNavigation))
const menuSearch = ref('')
const activeNav = ref('')
const tabs = ref<WorkspaceTab[]>([{ path: '/', titleKey: 'common.home', fallback: '' }])
const pathname = (path: string) => router.resolve(path).path
const tabKey = 'sma.tabs.' + auth.user?.id
if (preferences.keepTabsAfterReload) {
  try {
    const saved = JSON.parse(localStorage.getItem(tabKey) || '[]') as WorkspaceTab[]
    saved
      .filter((tab) => {
        if (
          typeof tab.path !== 'string' ||
          !tab.path.startsWith('/') ||
          tab.path.startsWith('//') ||
          tab.path === '/'
        )
          return false
        const resolved = router.resolve(tab.path)
        return (
          resolved.meta.titleKey !== 'common.notFound' &&
          (!resolved.meta.permission || auth.hasPermission(String(resolved.meta.permission)))
        )
      })
      .forEach((tab) => {
        tabs.value = trackPageTab(tabs.value, tab, pathname)
      })
  } catch {
    /* Invalid saved tabs are ignored. */
  }
}
watch(
  [tabs, () => preferences.keepTabsAfterReload],
  () => {
    if (preferences.keepTabsAfterReload) localStorage.setItem(tabKey, JSON.stringify(tabs.value))
    else localStorage.removeItem(tabKey)
  },
  { deep: true },
)
function tabLabel(tab: WorkspaceTab) {
  return tab.titleKey && te(tab.titleKey)
    ? t(tab.titleKey)
    : findMenu(auth.user?.menus || [], (menu) => menu.path === router.resolve(tab.path).path)
        ?.name || tab.fallback
}
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
    if (compact.value) collapsed.value = true
    if (route.path !== '/403')
      tabs.value = trackPageTab(
        tabs.value,
        {
          path,
          titleKey: typeof route.meta.titleKey === 'string' ? route.meta.titleKey : undefined,
          fallback: title.value,
        },
        pathname,
      )
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
void branding.load()
</script>
<template>
  <div class="admin-shell" :class="{ collapsed }">
    <button
      v-if="compact && !collapsed"
      class="navigation-backdrop"
      :aria-label="t('common.collapse')"
      @click="collapsed = true"
    />
    <aside class="sidebar">
      <RouterLink class="brand" to="/"
        ><img v-if="branding.compactLogo" :src="branding.compactLogo" :alt="branding.name" style="width:36px;height:36px;object-fit:contain" /><b v-else>{{ branding.entry?.branding.identity.shortName || t('common.shortBrand') }}</b
        ><span>{{ branding.name }}</span></RouterLink
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
        <el-menu-item index="/" :aria-label="t('common.home')"
          ><MenuIcon name="HomeFilled" /><template #title>{{
            t('common.home')
          }}</template></el-menu-item
        >
        <MenuBranch v-for="item in menus" :key="item.id" :item="item" />
      </el-menu>
      <div class="sidebar-footer">{{ branding.name }}</div>
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
          <el-badge
            :value="
              Number(messages.summary.unreadNotices) +
              Number(messages.summary.pendingTodos) +
              Number(messages.summary.unreadMessages)
            "
            :max="99"
            :hidden="
              messages.summary.unreadNotices === '0' &&
              messages.summary.pendingTodos === '0' &&
              messages.summary.unreadMessages === '0'
            "
            ><el-button
              text
              :aria-label="t('message.center')"
              @click="router.push('/message/inbox')"
              ><el-icon><Bell /></el-icon></el-button
          ></el-badge>
          <DisplaySettings /><AvatarImage :file-id="auth.user?.avatarFileId" /><el-button
            text
            @click="router.push('/profile')"
            >{{ auth.user?.name }}</el-button
          ><el-button text @click="router.push('/preferences')">{{
            t('common.preferences')
          }}</el-button
          ><el-button text @click="router.push('/auth/security')">{{ t('auth.accountSecurity') }}</el-button><SecurityControls /><el-button @click="logout">{{ t('common.signOut') }}</el-button>
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
      <div v-if="preferences.tabsEnabled" class="workspace-tabs">
        <el-tag
          v-for="tab in tabs"
          :key="tab.path"
          :closable="pathname(tab.path) !== '/'"
          :type="route.fullPath === tab.path ? 'primary' : 'info'"
          @click="router.push(tab.path)"
          @close="closeTab(tab.path)"
          >{{ tabLabel(tab) }}</el-tag
        >
      </div>
      <el-alert
        v-if="auth.user?.passwordChangeSuggested"
        :title="t('auth.changeSuggested')"
        type="warning"
        :closable="false"
      />
      <main class="main-content"><RouterView :key="route.path" /></main>
      <BrandingFooter />
    </div>
  </div>
</template>
