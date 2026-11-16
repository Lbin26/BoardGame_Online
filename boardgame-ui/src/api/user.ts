import { request } from '@/utils/request'

/** 用户信息 */
export interface UserInfo {
  userId: number
  userName: string
  nickName: string
  [key: string]: unknown
}

/** 个人中心返回体（R<ProfileVo> 的 data） */
export interface ProfileVo {
  user: UserInfo
  roleGroup: string
  postGroup: string
}

/** 获取当前登录用户信息 */
export function getProfile(): Promise<ProfileVo> {
  return request.get('/system/user/profile')
}
