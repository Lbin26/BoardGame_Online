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
  /** 头像 OSS ID（RuoYi 原生字段，存 ossId 而非 URL） */
  avatar?: number | null
  /** 头像访问地址（后端 @Translation 自动翻译 avatar 得到） */
  avatarUrl?: string | null
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
  /** 头像 OSS ID（上传头像后写入） */
  avatar?: number | null
}

/** 头像上传返回体 */
export interface AvatarUploadVo {
  /** 写入 sys_user.avatar 的 OSS ID */
  ossId: number
  /** 头像访问地址 */
  url: string
}

/** 获取当前登录用户信息 */
export function getProfile(): Promise<ProfileVo> {
  return request.get('/system/user/profile')
}

/** 更新当前登录用户的个人资料（名字/性别/年龄/偏好游戏类型） */
export function updateProfile(body: UpdateProfileBody): Promise<void> {
  return request.put('/system/user/profile', body)
}

/**
 * 上传头像：后端本地存储并直接写 sys_user.avatar，返回头像访问地址。
 * 走标准 request（R<AvatarUploadVo> 信封，拦截器会自动解包成 data）。
 */
export function uploadAvatar(file: File): Promise<AvatarUploadVo> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 15000,
  })
}
