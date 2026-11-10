import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/lib/token'
import { useAuth } from '@/stores/auth'
import { loadLocaleModule } from '@/locales'
import { accessDecision } from './access'
import { resources } from '@/features/iam/resources'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, module: 'auth', titleKey: 'auth.loginTitle' },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        {
          path: 'monitor/server',
          component: () => import('@/views/monitor/ServerView.vue'),
          meta: {
            module: 'monitor',
            titleKey: 'monitor.server',
            permission: 'monitor:server:view',
          },
        },
        {
          path: 'monitor/caches',
          component: () => import('@/views/monitor/CachesView.vue'),
          meta: { module: 'monitor', titleKey: 'monitor.caches', permission: 'monitor:cache:view' },
        },
        {
          path: 'monitor/events',
          component: () => import('@/views/monitor/PublicationsView.vue'),
          meta: { module: 'monitor', titleKey: 'monitor.events', permission: 'monitor:event:view' },
        },
        {
          path: 'monitor/database',
          component: () => import('@/views/monitor/DatabaseView.vue'),
          meta: {
            module: 'monitor',
            titleKey: 'monitor.database',
            permission: 'monitor:database:view',
          },
        },
        {
          path: 'message/inbox',
          component: () => import('@/views/message/InboxView.vue'),
          meta: { module: 'message', titleKey: 'message.center' },
        },
        {
          path: 'message/todos',
          component: () => import('@/views/message/TasksView.vue'),
          props: { managed: true },
          meta: {
            module: 'message',
            titleKey: 'message.taskManagement',
            permission: 'message:todo:view',
          },
        },
        {
          path: 'message/notices',
          component: () => import('@/views/message/NoticesView.vue'),
          meta: {
            module: 'message',
            titleKey: 'message.notices',
            permission: 'message:notice:view',
          },
        },
        {
          path: 'profile',
          component: () => import('@/views/iam/ProfileView.vue'),
          meta: { module: 'iam', titleKey: 'common.profile' },
        },
        {
          path: 'file/files',
          component: () => import('@/views/file/FilesView.vue'),
          meta: { module: 'file', titleKey: 'file.files', permission: 'file:file:view' },
        },
        ...Object.values(resources)
          .filter((resource) => resource.module !== 'config')
          .map((resource) => ({
            path: 'iam/' + resource.id,
            component: () => import('@/views/iam/ManagementView.vue'),
            meta: {
              module: 'iam',
              resource: resource.id,
              titleKey: resource.titleKey,
              permission: resource.permission + ':view',
            },
          })),
        {
          path: 'auth/sessions',
          component: () => import('@/views/auth/SessionsView.vue'),
          meta: { module: 'auth', titleKey: 'auth.sessions', permission: 'auth:session:view' },
        },
        { path: 'frame/:id', component: () => import('@/views/FrameView.vue') },
        {
          path: 'config/dictionaries',
          component: () => import('@/views/config/DictionaryView.vue'),
          meta: {
            module: 'config',
            titleKey: 'config.dictionary',
            permission: 'config:dictionary:view',
          },
        },
        {
          path: 'config/parameters',
          component: () => import('@/views/config/ParametersView.vue'),
          meta: {
            module: 'config',
            titleKey: 'config.parameters',
            permission: 'config:parameter:view',
          },
        },
        {
          path: 'preferences',
          component: () => import('@/views/config/PreferencesView.vue'),
          meta: { module: 'config', titleKey: 'common.preferences' },
        },
        {
          path: 'audit/logins',
          component: () => import('@/views/audit/LogsView.vue'),
          meta: {
            module: 'audit',
            logKind: 'logins',
            titleKey: 'audit.logins',
            permission: 'audit:login:view',
          },
        },
        {
          path: 'audit/operations',
          component: () => import('@/views/audit/LogsView.vue'),
          meta: {
            module: 'audit',
            logKind: 'operations',
            titleKey: 'audit.operations',
            permission: 'audit:operation:view',
          },
        },
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { titleKey: 'common.home' },
        },
        {
          path: '403',
          component: () => import('@/views/StatusView.vue'),
          meta: { titleKey: 'common.forbidden' },
        },
        {
          path: ':pathMatch(.*)*',
          component: () => import('@/views/StatusView.vue'),
          meta: { titleKey: 'common.notFound' },
        },
      ],
    },
  ],
})
router.beforeEach(async (to) => {
  await loadLocaleModule(typeof to.meta.module === 'string' ? to.meta.module : 'common')
  const auth = useAuth()
  if (!to.meta.public && getToken() && !auth.ready) {
    try {
      await auth.load()
    } catch {
      auth.reset()
    }
  }
  const decision = accessDecision(
    to.meta.public === true,
    !!getToken() && !!auth.user,
    typeof to.meta.permission === 'string' ? to.meta.permission : undefined,
    auth.hasPermission,
  )
  if (decision === 'login') return { name: 'login', query: { redirect: to.fullPath } }
  if (decision === 'forbidden') return '/403'
})
export default router
