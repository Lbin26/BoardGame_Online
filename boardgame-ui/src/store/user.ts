import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import router from '@/router'
import * as authApi from '@/api/auth'
import * as userApi from '@/api/user'
import { TOKEN_KEY, getToken } from '@/utils/request'
import type { LoginBody } from '@/api/auth'
import type { UserInfo } from '@/api/user'

/** 用户状态 store：持有登录令牌与当前用户信息 */
export const useUserStore = defineStore('user', () => {
  /** 登录令牌（初始化时从 localStorage 水合，刷新后可恢复登录态） */
  const token = ref(getToken() ?? '')
  /** 当前登录用户信息 */
  const userInfo = ref<UserInfo | null>(null)

  /** 是否已登录（令牌非空） */
  const isLogin = computed(() => token.value !== '')

  /** 登录：调登录接口，写令牌到 localStorage 与 state，再拉取用户信息 */
  async function login(form: LoginBody): Promise<void> {
    const data = await authApi.login(form)
    localStorage.setItem(TOKEN_KEY, data.access_token)
    token.value = data.access_token
    await fetchProfile()
  }

  /** 拉取当前用户信息并写入 state */
  async function fetchProfile(): Promise<void> {
    const data = await userApi.getProfile()
    userInfo.value = data.user
  }

  /** 退出登录：调登出接口（忽略错误），清理本地状态并跳登录页 */
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 登出接口失败不阻断本地清理
    }
    localStorage.removeItem(TOKEN_KEY)
    token.value = ''
    userInfo.value = null
    router.push('/login')
  }

  return { token, userInfo, isLogin, login, fetchProfile, logout }
})
