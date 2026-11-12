import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import AdminLayout from '@/layouts/AdminLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '数据看板' },
        },
        {
          path: 'content',
          name: 'content',
          component: () => import('@/views/ContentView.vue'),
          meta: { title: '内容管理' },
        },
        {
          path: 'notices',
          name: 'notices',
          component: () => import('@/views/NoticesView.vue'),
          meta: { title: '公告管理' },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/MessagesView.vue'),
          meta: { title: '消息推送' },
        },
        {
          path: 'paths',
          name: 'paths',
          component: () => import('@/views/LearningPathsView.vue'),
          meta: { title: '学习路线' },
        },
        {
          path: 'interview',
          name: 'interview',
          component: () => import('@/views/InterviewSettingsView.vue'),
          meta: { title: '面试设置' },
        },
        {
          path: 'students',
          name: 'students',
          component: () => import('@/views/StudentsView.vue'),
          meta: { title: '学生管理' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/AdminUsersView.vue'),
          meta: { title: '管理员与权限' },
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/views/LogsView.vue'),
          meta: { title: '操作日志' },
        },
        {
          path: 'orphan-files',
          name: 'orphan-files',
          component: () => import('@/views/OrphanFilesView.vue'),
          meta: { title: '孤儿文件巡检' },
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '管理员登录' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true, title: '页面不存在' },
    },
  ],
})

const guardEnabled = import.meta.env.VITE_AUTH_GUARD_ENABLED === 'true'

// 登录态恢复只做一次，且必须早于首个路由守卫的判定；
// useSessionStore 必须在 pinia 安装后调用，故放在守卫内而非模块顶层。
let restoring: Promise<void> | null = null
function ensureRestored(): Promise<void> {
  if (!guardEnabled) {
    return Promise.resolve()
  }
  if (!restoring) {
    restoring = useSessionStore().restore()
  }
  return restoring
}

router.beforeEach(async (to) => {
  await ensureRestored()
  const session = useSessionStore()
  if (guardEnabled && !to.meta.public && !session.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (guardEnabled && to.name === 'login' && session.isAuthenticated) {
    return { name: 'dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 云枢智学管理后台` : '云枢智学管理后台'
})

export default router
