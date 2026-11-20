import { createRouter, createWebHistory } from 'vue-router'

import { pinia } from '@/store'
import { useUserStore } from '@/store/user'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/home/index.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/login/index.vue') },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/register/index.vue'),
    },
    {
      path: '/lobby',
      name: 'lobby',
      component: () => import('@/views/lobby/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/game/:roomId',
      name: 'game',
      component: () => import('@/views/game/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/design',
      name: 'design',
      component: () => import('@/views/design/index.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

/** 全局路由守卫：校验登录态 */
router.beforeEach((to) => {
  const userStore = useUserStore(pinia)

  // 需登录但未登录：重定向到登录页并携带回跳地址
  if (to.meta.requiresAuth && !userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录访问登录/注册页：重定向首页
  if (userStore.isLogin && (to.path === '/login' || to.path === '/register')) {
    return { path: '/' }
  }
})

export default router
