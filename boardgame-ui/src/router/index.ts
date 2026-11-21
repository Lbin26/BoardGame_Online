import { createRouter, createWebHistory } from 'vue-router'

import { pinia } from '@/store'
import { useUserStore } from '@/store/user'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 根路径直接进大厅；原游戏选择页（象棋/五子棋卡片）已删除
    { path: '/', redirect: '/lobby' },
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
      path: '/rules/:gameKey',
      name: 'rules',
      component: () => import('@/views/rules/index.vue'),
    },
    {
      path: '/game/:roomId',
      name: 'game',
      component: () => import('@/views/game/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/splendor/:roomId',
      name: 'splendor',
      component: () => import('@/views/splendor/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/design',
      name: 'design',
      component: () => import('@/views/design/index.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/profile/index.vue'),
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

  // 已登录访问登录/注册页：重定向大厅
  if (userStore.isLogin && (to.path === '/login' || to.path === '/register')) {
    return { path: '/lobby' }
  }
})

export default router
