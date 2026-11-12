import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import AppLayout from '@/layouts/AppLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    hideTabbar?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'routes',
          name: 'routes',
          component: () => import('@/views/LearningPathsView.vue'),
          meta: { title: '学习路线' },
        },
        {
          path: 'routes/:pathId',
          name: 'route-detail',
          component: () => import('@/views/PathDetailView.vue'),
          props: true,
          meta: { title: '路线详情', hideTabbar: true },
        },
        {
          path: 'stages/:stageId',
          name: 'stage',
          component: () => import('@/views/StageView.vue'),
          props: true,
          meta: { title: '阶段学习', hideTabbar: true },
        },
        {
          path: 'quiz/:stageId',
          name: 'quiz',
          component: () => import('@/views/QuizView.vue'),
          props: true,
          meta: { title: '随堂练习', hideTabbar: true },
        },
        {
          path: 'wrongbook',
          name: 'wrongbook',
          component: () => import('@/views/WrongBookView.vue'),
          meta: { title: '错题本' },
        },
        {
          path: 'retry',
          name: 'retry',
          component: () => import('@/views/QuizView.vue'),
          props: { mode: 'retry' },
          meta: { title: '错题重练', hideTabbar: true },
        },
        {
          path: 'interview',
          name: 'interview',
          component: () => import('@/views/InterviewView.vue'),
          meta: { title: '模拟面试' },
        },
        {
          path: 'interview/sessions/:sessionId',
          name: 'interview-session',
          component: () => import('@/views/InterviewSessionView.vue'),
          props: true,
          meta: { title: '面试进行中', hideTabbar: true },
        },
        {
          path: 'interview/sessions/:sessionId/report',
          name: 'interview-report',
          component: () => import('@/views/InterviewReportView.vue'),
          props: true,
          meta: { title: '面试报告', hideTabbar: true },
        },
        {
          path: 'records',
          name: 'records',
          component: () => import('@/views/RecordsView.vue'),
          meta: { title: '学习记录' },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/MessagesView.vue'),
          meta: { title: '消息中心', hideTabbar: true },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '我的' },
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true, title: '注册' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true, title: '页面不存在' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
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
  if (guardEnabled && to.meta.public && session.isAuthenticated && (to.name === 'login' || to.name === 'register')) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 云枢智学` : '云枢智学'
})

export default router
