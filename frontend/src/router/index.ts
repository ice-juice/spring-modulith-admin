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
        ...Object.values(resources).map((resource) => ({
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
