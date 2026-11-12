<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter, isNavigationFailure, NavigationFailureType } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Layers, PanelLeftClose, PanelLeftOpen, Search, Bell, ChevronRight, LogOut, UserRound, Settings, X, LayoutDashboard, PanelsTopLeft } from '@lucide/vue'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import GlobalSearch from '@/components/business/GlobalSearch.vue'
import OperationPanel from '@/components/business/OperationPanel.vue'
import PersonalShelf from '@/features/workspace/PersonalShelf.vue'
import { useShelfNotes } from '@/features/workspace/notes'
import { useShelf } from '@/features/workspace/shelf'
import PinPageButton from '@/features/workspace/PinPageButton.vue'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import DisplaySettings from '@/components/DisplaySettings.vue'
import NavigationTree from '@/components/NavigationTree.vue'
import NavigationGroupSwitcher from '@/components/NavigationGroupSwitcher.vue'
import SecurityControls from '@/components/SecurityControls.vue'
import AvatarImage from '@/components/AvatarImage.vue'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { useBranding } from '@/stores/branding'
import { useMessages } from '@/stores/messages'
import { findMenu, menuRoute, menusForNavigation } from '@/lib/menus'
import { useWorkspaceHeight } from '@/composables/useWorkspaceHeight'
import { trackPageTab, type WorkspaceTab } from '@/lib/tabs'
import { confirmAction } from '@/lib/feedback'
import { shelfNavigate } from '@/features/workspace/navigation'
import { safeLocalPath } from '@/lib/web-addresses'
const route = useRoute(), router = useRouter(), { t } = useI18n(), auth = useAuth(), preferences = usePreferences(), branding = useBranding(), messages = useMessages()
const shelfDock = ref(0)
const shelf = useShelf()
const workspaceHeight = useWorkspaceHeight()
const security = ref<InstanceType<typeof SecurityControls>>()
const operationsPanel = ref<InstanceType<typeof OperationPanel>>()
const collapsed = ref(false), mobileOpen = ref(false), searchOpen = ref(false), activeNav = ref('')
const currentNavigation = computed(() => auth.user?.navigation.find(n => n.id === activeNav.value))
const groupMenus = computed(() => menusForNavigation(auth.user?.menus || [], currentNavigation.value?.menuIds))
const menus = computed(() => groupMenus.value)
const navigationKey = computed(() => 'sma.next.navigation.v1.' + auth.user?.portal + '.' + auth.user?.id)
watch(() => auth.user?.id, () => {
  activeNav.value = ''
  try { const saved = sessionStorage.getItem(navigationKey.value); if (auth.user?.navigation.some(group => group.id === saved)) activeNav.value = saved! } catch { /* Storage is optional. */ }
}, { immediate: true })
watch(() => auth.user?.navigation, () => { if (activeNav.value && !currentNavigation.value) activeNav.value = '' })
watch(activeNav, value => { try { if (auth.user) sessionStorage.setItem(navigationKey.value, value) } catch { /* Storage is optional. */ } })
watch(() => route.path, path => {
  if (!activeNav.value || path === '/' || findMenu(groupMenus.value, item => menuRoute(item) === path)) return
  activeNav.value = auth.user?.navigation.find(group => findMenu(menusForNavigation(auth.user?.menus || [], group.menuIds), item => menuRoute(item) === path))?.id || ''
})
function focusNavigation() { if (!auth.user?.locked) { mobileOpen.value = false; searchOpen.value = true } }
function shortcut(event: KeyboardEvent) { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); void focusNavigation() } }

const currentMenu = computed(() => findMenu(auth.user?.menus || [], menu => menu.path === route.path))
const title = computed(() => currentMenu.value?.name || (typeof route.meta.titleKey === 'string' ? t(route.meta.titleKey) : t('common.workspace')))
const count = computed(() => Number(messages.summary.unreadNotices) + Number(messages.summary.pendingTodos) + Number(messages.summary.unreadMessages))
const countLabel = computed(() => count.value > 99 ? '99+' : String(count.value))
const tabs = ref<WorkspaceTab[]>([]), tabKey = computed(() => 'sma.next.tabs.' + auth.user?.id)
provide(shelfNavigate, async path => {
  const result = await router.push(safeLocalPath(tabs.value.find(tab => tab.path.split(/[?#]/)[0] === path)?.path) || path)
  return isNavigationFailure(result, NavigationFailureType.duplicated) ? undefined : result
})
watch(() => auth.user?.id, () => { tabs.value = []; if (!preferences.keepTabsAfterReload) return; try { const stored = JSON.parse(localStorage.getItem(tabKey.value) || '[]'); if (Array.isArray(stored)) tabs.value = stored.filter(tab => typeof tab.path === 'string' && tab.path.startsWith('/') && !tab.path.startsWith('//')).slice(0, 30) } catch { /* Ignore invalid persisted UI preferences. */ } }, { immediate: true })
watch(() => route.fullPath, () => { tabs.value = trackPageTab(tabs.value, { path: route.fullPath, titleKey: typeof route.meta.titleKey === 'string' ? route.meta.titleKey : undefined, fallback: title.value }, path => path.split('?')[0] || '/'); mobileOpen.value = false }, { immediate: true })
watch([tabs, () => preferences.keepTabsAfterReload], () => { if (preferences.keepTabsAfterReload) localStorage.setItem(tabKey.value, JSON.stringify(tabs.value)); else localStorage.removeItem(tabKey.value) }, { deep: true })
watch(() => auth.user?.locked, locked => { if (locked) messages.pause(); else void messages.connect() })
function label(tab: WorkspaceTab) { return tab.titleKey ? t(tab.titleKey) : findMenu(auth.user?.menus || [], menu => menu.path === tab.path.split('?')[0])?.name || tab.fallback }
function close(tab: WorkspaceTab) { const index = tabs.value.indexOf(tab); tabs.value = tabs.value.filter(item => item !== tab); if (route.fullPath === tab.path) void router.push(tabs.value[Math.max(0, index - 1)]?.path || '/') }
async function beforeExit() {
  const notes = useShelfNotes()
  if (auth.user?.locked && notes.dirty) return confirmAction(t('common.shelf.lockedExit'), t('common.confirm'), true)
  return notes.flushAll()
}
async function logout() { await security.value?.logout() }
onMounted(() => { void branding.load(); messages.start(); window.addEventListener('keydown', shortcut) })
onUnmounted(() => { messages.stop(); window.removeEventListener('keydown', shortcut) })

</script>
<template>
  <div class="admin-shell min-h-dvh lg:grid" :class="[collapsed ? 'lg:grid-cols-[76px_minmax(0,1fr)]' : 'lg:grid-cols-[240px_minmax(0,1fr)]', { 'table-layout': route.meta.contentLayout === 'table' }]" :style="{ '--workspace-viewport-height': workspaceHeight, paddingRight: shelfDock + 'px' }">
    <aside class="admin-sidebar sticky top-0 hidden h-dvh min-h-0 flex-col lg:flex">
      <RouterLink to="/" class="flex h-20 shrink-0 items-center gap-3 px-5" :aria-label="branding.name">
        <img v-if="branding.compactLogo" :src="branding.compactLogo" :alt="branding.name" class="size-10 shrink-0 object-contain" /><span v-else class="brand-symbol"><Layers class="size-5" /></span><span v-if="!collapsed" class="min-w-0"><span class="block truncate text-base font-semibold tracking-tight" :title="branding.name">{{ branding.name }}</span><span class="softlight-eyebrow block text-[10px]">{{ t('common.workspaceEyebrow') }}</span></span>
      </RouterLink>
      <div class="mx-3 mb-4"><NavigationGroupSwitcher v-model="activeNav" :groups="auth.user?.navigation || []" :collapsed="collapsed" /></div>
      <p v-if="!collapsed" class="softlight-eyebrow mx-6 mb-3">{{ t('common.navigation') }}</p>
      <ScrollArea class="flex-1" data-scroll-region="sidebar" :label="t('common.navigation')"><nav class="px-3 pb-4" :aria-label="t('common.navigation')"><RouterLink to="/" class="navigation-home mb-1 flex min-h-11 items-center gap-2.5 rounded-xl px-3 text-sm" :class="route.path === '/' ? 'bg-accent font-medium text-accent-foreground' : 'text-muted-foreground hover:bg-muted'" :aria-label="t('common.home')" :aria-current="route.path === '/' ? 'page' : undefined" :title="collapsed ? t('common.home') : undefined"><LayoutDashboard class="size-[18px] shrink-0" /><span v-if="!collapsed">{{ t('common.home') }}</span></RouterLink><NavigationTree :items="menus" :collapsed="collapsed" /></nav></ScrollArea>
      <div class="mx-3 border-t py-3"><Button variant="ghost" class="w-full justify-start text-muted-foreground" :aria-label="t(collapsed ? 'common.expand' : 'common.collapse')" @click="collapsed = !collapsed"><PanelLeftOpen v-if="collapsed" class="size-4" /><PanelLeftClose v-else class="size-4" /><span v-if="!collapsed">{{ t('common.collapse') }}</span></Button></div>
    </aside>
    <div class="admin-body flex min-w-0 flex-col">
      <div class="admin-brand-watermark" aria-hidden="true">{{ branding.name }}</div>
      <header class="admin-topbar sticky top-0 z-40 flex min-h-[68px] items-center justify-between gap-3 border-b px-4 md:px-8">
        <div class="flex min-w-0 items-center gap-3"><Button class="lg:hidden" variant="ghost" size="icon" :aria-label="t('common.expand')" @click="mobileOpen = true"><PanelLeftOpen class="size-5" /></Button><RouterLink to="/" class="hidden max-w-40 shrink-0 truncate text-xs text-muted-foreground sm:block">{{ branding.entry?.branding.identity.shortName || t('common.workspace') }}</RouterLink><ChevronRight class="hidden size-3.5 shrink-0 text-muted-foreground sm:block" /><span class="truncate text-sm font-medium">{{ title }}</span></div>
        <div class="flex shrink-0 items-center gap-1"><Button variant="ghost" class="global-search-trigger gap-2 px-2 text-xs text-muted-foreground xl:mr-3" :aria-label="t('common.unifiedSearch.title')" @click="focusNavigation"><Search class="size-4 shrink-0" /><span class="global-search-label">{{ t('common.unifiedSearch.title') }}</span><kbd class="global-search-label ml-4 rounded border px-1.5 py-0.5 text-[10px]">{{ t('common.searchShortcut') }}</kbd></Button><Button variant="ghost" size="icon" class="relative" :aria-label="t('message.center')" @click="router.push('/message/inbox')"><Bell class="size-4" /><span v-if="count" class="absolute top-0 right-0 min-w-4 rounded-full bg-primary px-1 text-[10px] leading-4 text-primary-foreground">{{ countLabel }}</span></Button><Button v-if="auth.user && !auth.user.locked" variant="ghost" size="icon" :aria-label="t('common.shelf.title')" :title="t('common.shelf.title')" :aria-expanded="shelf.open" @click="shelf.show()"><PanelsTopLeft class="size-4" /></Button><OperationPanel ref="operationsPanel" /><DisplaySettings /><DropdownMenu><DropdownMenuTrigger as-child><Button variant="ghost" class="gap-2 px-1.5" :aria-label="t('common.account')"><AvatarImage :file-id="auth.user?.avatarFileId" :alt="auth.user?.name" :size="32" /><span class="account-name hidden max-w-28 truncate text-sm md:inline">{{ auth.user?.name }}</span></Button></DropdownMenuTrigger><DropdownMenuContent align="end" class="w-52"><DropdownMenuLabel>{{ auth.user?.username }}</DropdownMenuLabel><DropdownMenuSeparator /><DropdownMenuItem @select="router.push('/profile')"><UserRound class="size-4" />{{ t('common.profile') }}</DropdownMenuItem><DropdownMenuItem @select="router.push('/preferences')"><Settings class="size-4" />{{ t('common.preferences') }}</DropdownMenuItem><DropdownMenuSeparator /><DropdownMenuItem @select="security?.lock()">{{ t('auth.lock') }}</DropdownMenuItem><DropdownMenuItem @select="router.push('/auth/security')">{{ t('auth.accountSecurity') }}</DropdownMenuItem><DropdownMenuItem @select="security?.openPassword()">{{ t('auth.changePassword') }}</DropdownMenuItem><DropdownMenuSeparator /><DropdownMenuItem @select="logout"><LogOut class="size-4" />{{ t('common.signOut') }}</DropdownMenuItem></DropdownMenuContent></DropdownMenu></div>
      </header>
      <ScrollArea v-if="preferences.tabsEnabled" orientation="horizontal" class="h-11 border-b" data-scroll-region="work-tabs" :label="t('common.workTabs')"><div class="flex h-full w-max min-w-full items-center gap-1 px-4 py-1.5 md:px-8"><div v-for="tab in tabs" :key="tab.path.split('?')[0]" class="work-tab flex max-w-64 shrink-0 items-center rounded-lg" :class="route.fullPath === tab.path ? 'is-active' : 'text-muted-foreground'"><button class="truncate px-3 py-1.5 text-xs" :aria-current="route.fullPath === tab.path ? 'page' : undefined" @click="router.push(tab.path)">{{ label(tab) }}</button><PinPageButton :path="tab.path" menu /><button v-if="tab.path !== '/'" class="mr-1 flex size-7 items-center justify-center rounded hover:bg-muted" :aria-label="t('common.closeTab', { name: label(tab) })" @click="close(tab)"><X class="size-3" /></button></div></div></ScrollArea>
      <main id="main-content" class="admin-content mx-auto w-full max-w-[1680px] flex-1 px-4 py-4 md:px-8"><Alert v-if="auth.user?.passwordChangeSuggested" class="mb-5 border-[var(--warning)]/30 bg-[var(--warning)]/5"><AlertDescription class="flex flex-wrap items-center justify-between gap-2"><span>{{ t('auth.changeSuggested') }}</span><Button variant="outline" size="sm" @click="security?.openPassword()">{{ t('auth.changePassword') }}</Button></AlertDescription></Alert><div v-if="auth.user && !auth.user.locked && (operationsPanel?.attentionCount || operationsPanel?.unavailable)" class="mb-3 flex shrink-0 flex-col items-start justify-between gap-2 rounded-lg border bg-card px-3 py-2 text-sm sm:flex-row sm:items-center" role="status" aria-live="polite" data-operation-recovery><span class="min-w-0 flex-1">{{ t(operationsPanel?.unavailable ? 'common.operationPanel.unavailableHint' : 'common.operationPanel.leaveHint', { count: operationsPanel?.attentionCount || 0 }) }}</span><Button variant="outline" size="sm" @click="operationsPanel?.show()">{{ t('common.operationPanel.review') }}</Button></div><RouterView /></main>
    </div>
    <PersonalShelf @dock="shelfDock = $event" /><GlobalSearch v-model="searchOpen" />
    <SecurityControls ref="security" :before-exit="beforeExit" />
    <Sheet v-model:open="mobileOpen"><SheetContent side="left" class="flex w-80 max-w-[90vw] flex-col p-0"><SheetHeader class="border-b p-5"><SheetTitle>{{ branding.name }}</SheetTitle><SheetDescription>{{ t('common.navigation') }}</SheetDescription></SheetHeader><div class="space-y-3 px-4"><NavigationGroupSwitcher v-model="activeNav" :groups="auth.user?.navigation || []" /></div><ScrollArea class="min-h-0 flex-1" :label="t('common.navigation')"><nav class="px-3 pb-6" :aria-label="t('common.navigation')"><Button variant="ghost" class="mb-1 w-full justify-start" @click="router.push('/'); mobileOpen = false"><LayoutDashboard class="size-4" />{{ t('common.home') }}</Button><NavigationTree :items="menus" @navigate="mobileOpen = false" /></nav></ScrollArea></SheetContent></Sheet>
  </div>
</template>
