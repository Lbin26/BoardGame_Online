import { request } from '@/utils/request'

/** 用户信息 */
export interface UserInfo {
  userId: number
  userName: string
  nickName: string
  /** 性别：0男 1女 2未知 */
  gender?: string
  /** 年龄（DiceGo 扩展） */
  age?: number | null
  /** 偏好游戏分区 key，逗号分隔（DiceGo 扩展） */
  favGameTypes?: string
  [key: string]: unknown
}

/** 个人中心返回体（R<ProfileVo> 的 data） */
export interface ProfileVo {
  user: UserInfo
  roleGroup: string
  postGroup: string
}

/** 个人资料更新入参 */
export interface UpdateProfileBody {
  nickName: string
  gender?: string
  age?: number | null
  favGameTypes?: string
}

/** 获取当前登录用户信息 */
export function getProfile(): Promise<ProfileVo> {
  return request.get('/system/user/profile')
}

/** 更新当前登录用户的个人资料（名字/性别/年龄/偏好游戏类型） */
export function updateProfile(body: UpdateProfileBody): Promise<void> {
  return request.put('/system/user/profile', body)
}
