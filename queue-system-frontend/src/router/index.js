import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/home', redirect: '/admin' },
  { path: '/login', component: () => import('../pages/LoginPage.vue'), meta: { title: '登录', public: true } },
  { path: '/kiosk', redirect: '/appointment' },
  { path: '/appointment', component: () => import('../pages/AppointmentPage.vue'), meta: { title: '扫码取号', public: true } },
  { path: '/display', component: () => import('../pages/DisplayBoardPage.vue'), meta: { title: '叫号大屏', public: true } },
  { path: '/counter', component: () => import('../pages/CounterPage.vue'), meta: { title: '窗口工作台' } },
  { path: '/admin', component: () => import('../pages/AdminPage.vue'), meta: { title: '管理后台' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isPublic = to.meta.public === true

  // Public routes don't need authentication
  if (isPublic) {
    next()
    return
  }

  // Check if logged in
  if (!userStore.isLoggedIn) {
    next('/login')
    return
  }

  // For window operators, only allow counter page
  if (userStore.isWindowOperator && to.path !== '/counter') {
    next('/counter')
    return
  }

  next()
})

router.afterEach((to) => {
  document.title = to.meta.title ? `\u200b${to.meta.title} — 排队叫号系统` : '排队叫号系统'
})

export default router
