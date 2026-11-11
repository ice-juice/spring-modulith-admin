import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/lib/token'
import { useAuth } from '@/stores/auth'
import { loadLocaleModule } from '@/locales'
import { accessDecision } from './access'
const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes: [
  { path: '/brand-preview', component: () => import('@/features/tenant/BrandPreviewFrame.vue'), meta: { public: true, module: 'tenant' } },
  { path: '/login/totp', component: () => import('@/features/auth/TotpPage.vue'), meta: { public: true, module: 'auth', titleKey: 'auth.totpTitle' } },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, module: 'auth' } },
  ...(import.meta.env.DEV ? [{ path: '/dev/components', component: () => import('@/views/ComponentGallery.vue'), meta: { public: true, module: 'common', localeModules: ['iam', 'workspace'] } }] : []),
  ...(import.meta.env.DEV ? [{ path: '/dev/rule', component: () => import('@/features/rule/EditorProbe.vue'), meta: { public: true, module: 'rule' } }] : []),
  { path: '/', component: () => import('@/layouts/AdminLayout.vue'), children: [
    { path: 'tenant/brand', component: () => import('@/features/tenant/BrandPage.vue'), meta: { module: 'tenant', titleKey: 'tenant.title', permission: 'tenant:brand:view' } },
    { path: 'tenant/tenants', component: () => import('@/features/tenant/TenantsPage.vue'), meta: { contentLayout: 'table', module: 'tenant', titleKey: 'tenant.directory.tenants', permission: 'tenant:tenant:view' } },
    { path: 'tenant/domains', component: () => import('@/features/tenant/DomainsPage.vue'), meta: { contentLayout: 'table', module: 'tenant', titleKey: 'tenant.directory.domains', permission: 'tenant:domain:view' } },
    { path: 'auth/security', component: () => import('@/features/auth/SecurityPage.vue'), meta: { module: 'auth', titleKey: 'auth.accountSecurity' } },
    { path: 'extension/manage', component: () => import('@/features/extension/ExtensionPage.vue'), meta: { module: 'extension', titleKey: 'extension.title', permission: 'extension:console:view' } },
    { path: '', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { module: 'workspace', titleKey: 'common.home' } },
    { path: 'iam/users', component: () => import('@/features/iam/UsersPage.vue'), meta: { contentLayout: 'table', module: 'iam', titleKey: 'iam.user', permission: 'iam:user:view' } },
    { path: 'iam/roles', component: () => import('@/features/iam/RolesPage.vue'), meta: { module: 'iam', titleKey: 'iam.role', permission: 'iam:role:view' } },
    { path: 'iam/departments', component: () => import('@/features/iam/DepartmentsPage.vue'), meta: { contentLayout: 'table', module: 'iam', titleKey: 'iam.department', permission: 'iam:department:view' } },
    { path: 'iam/posts', component: () => import('@/features/iam/PostsPage.vue'), meta: { contentLayout: 'table', module: 'iam', titleKey: 'iam.post', permission: 'iam:post:view' } },
    { path: 'iam/permissions', component: () => import('@/features/iam/PermissionsPage.vue'), meta: { contentLayout: 'table', module: 'iam', titleKey: 'iam.permission', permission: 'iam:permission:view' } },
    { path: 'iam/navigation', component: () => import('@/features/iam/NavigationPage.vue'), meta: { contentLayout: 'table', module: 'iam', titleKey: 'iam.navigation', permission: 'iam:navigation:view' } },
    { path: 'profile', component: () => import('@/features/iam/ProfilePage.vue'), meta: { module: 'iam', localeModules: ['file'], titleKey: 'common.profile' } },
    { path: 'preferences', component: () => import('@/features/config/PreferencesPage.vue'), meta: { module: 'config', titleKey: 'common.preferences' } },
    { path: 'config/dictionaries', component: () => import('@/features/config/DictionaryPage.vue'), meta: { contentLayout: 'table', module: 'config', titleKey: 'config.dictionary', permission: 'config:dictionary:view' } },
    { path: 'config/parameters', component: () => import('@/features/config/ParametersPage.vue'), meta: { contentLayout: 'table', module: 'config', titleKey: 'config.parameters', permission: 'config:parameter:view' } },
    { path: 'auth/sessions', component: () => import('@/features/auth/SessionsPage.vue'), meta: { contentLayout: 'table', module: 'auth', titleKey: 'auth.sessions', permission: 'auth:session:view' } },
    { path: 'audit/logins', component: () => import('@/features/audit/LogsPage.vue'), meta: { contentLayout: 'table', module: 'audit', titleKey: 'audit.logins', permission: 'audit:login:view', logKind: 'logins' } },
    { path: 'audit/operations', component: () => import('@/features/audit/LogsPage.vue'), meta: { contentLayout: 'table', module: 'audit', titleKey: 'audit.operations', permission: 'audit:operation:view', logKind: 'operations' } },
    { path: 'monitor/server', component: () => import('@/features/monitor/ServerPage.vue'), meta: { module: 'monitor', titleKey: 'monitor.server', permission: 'monitor:server:view' } },
    { path: 'monitor/sql', component: () => import('@/features/monitor/SqlPage.vue'), meta: { contentLayout: 'table', module: 'monitor', titleKey: 'monitor.sql', permission: 'monitor:sql:view' } },
    { path: 'monitor/caches', component: () => import('@/features/monitor/CachesPage.vue'), meta: { contentLayout: 'table', module: 'monitor', titleKey: 'monitor.caches', permission: 'monitor:cache:view' } },
    { path: 'monitor/events', component: () => import('@/features/monitor/PublicationsPage.vue'), meta: { contentLayout: 'table', module: 'monitor', titleKey: 'monitor.events', permission: 'monitor:event:view' } },
    { path: 'monitor/database', component: () => import('@/features/monitor/DatabasePage.vue'), meta: { contentLayout: 'table', module: 'monitor', titleKey: 'monitor.database', permission: 'monitor:database:view' } },
    { path: 'file/files', component: () => import('@/features/file/FilesPage.vue'), meta: { contentLayout: 'table', module: 'file', titleKey: 'file.files', permission: 'file:file:view' } },
    { path: 'frame/:id', component: () => import('@/views/FrameView.vue'), meta: { module: 'common' } },
    { path: 'message/inbox', component: () => import('@/features/message/InboxPage.vue'), meta: { contentLayout: 'table', module: 'message', localeModules: ['file'], titleKey: 'message.center' } },
    { path: 'message/todos', component: () => import('@/features/message/TasksPage.vue'), props: { managed: true }, meta: { contentLayout: 'table', module: 'message', localeModules: ['file'], titleKey: 'message.taskManagement', permission: 'message:todo:view' } },
    { path: 'message/notices', component: () => import('@/features/message/NoticesPage.vue'), meta: { contentLayout: 'table', module: 'message', localeModules: ['file'], titleKey: 'message.notices', permission: 'message:notice:view' } },
    { path: 'openapi/apps', component: () => import('@/features/openapi/ApplicationsPage.vue'), meta: { contentLayout: 'table', module: 'openapi', titleKey: 'openapi.apps', permission: 'openapi:app:view' } },
    { path: 'rule/scenes', component: () => import('@/features/rule/ScenesPage.vue'), meta: { module: 'rule', titleKey: 'rule.scenes', permission: 'rule:scene:view' } },
    { path: 'rule/templates', component: () => import('@/features/rule/TemplatesPage.vue'), meta: { module: 'rule', titleKey: 'rule.templates', permission: 'rule:template:view' } },
    { path: 'rule/templates/:id', component: () => import('@/features/rule/TemplateRoute.vue'), meta: { module: 'rule', titleKey: 'rule.templates', permission: 'rule:template:view' } },
    { path: 'rule/templates/new/:sceneId', component: () => import('@/features/rule/TemplateRoute.vue'), meta: { module: 'rule', titleKey: 'rule.newTemplate', permission: 'rule:template:edit' } },
    { path: 'rule/catalog', component: () => import('@/features/rule/CatalogPage.vue'), meta: { module: 'rule', titleKey: 'rule.catalog', permission: 'rule:catalog:view' } },
    { path: 'rule/instances', component: () => import('@/features/rule/InstancesPage.vue'), meta: { module: 'rule', titleKey: 'rule.instances', permission: 'rule:instance:view' } },
    { path: 'rule/operations', component: () => import('@/features/rule/OperationsPage.vue'), meta: { module: 'rule', titleKey: 'rule.operations', permission: 'rule:execution:view', anyPermission: ['rule:execution:view','rule:batch:view','rule:batch:migrate','rule:maintenance:view','rule:maintenance:clean'] } },
    { path: '403', component: () => import('@/views/StatusView.vue'), meta: { titleKey: 'common.forbidden' } },
    { path: ':pathMatch(.*)*', component: () => import('@/views/StatusView.vue'), meta: { titleKey: 'common.notFound' } },
  ] },
] })
router.beforeEach(async to => {
  await loadLocaleModule(typeof to.meta.module === 'string' ? to.meta.module : 'common')
  if (Array.isArray(to.meta.localeModules)) await Promise.all(to.meta.localeModules.filter((module): module is string => typeof module === 'string').map(loadLocaleModule))
  const auth = useAuth()
  if (!to.meta.public && getToken() && !auth.ready) { try { await auth.load() } catch { auth.reset() } }
  const decision = accessDecision(to.meta.public === true, !!getToken() && !!auth.user, typeof to.meta.permission === 'string' ? to.meta.permission : undefined, auth.hasPermission)
  if (decision === 'login') return { name: 'login', query: { redirect: to.fullPath } }
  if (decision === 'forbidden' && !(Array.isArray(to.meta.anyPermission) && auth.hasPermission(to.meta.anyPermission))) return '/403'
})
export default router
