import { request } from '@/utils/request'

/** 默认 Web/PC 客户端标识（对应后端 sys_client 种子数据） */
export const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e'

/** 登录请求体 */
export interface LoginBody {
  clientId: string
  grantType: 'password'
  username: string
  password: string
  code?: string
  uuid?: string
}

/** 登录成功返回的令牌信息（后端 LoginVo，字段为 snake_case） */
export interface LoginData {
  access_token: string
  refresh_token: string
  expire_in: number
  refresh_expire_in: number
  client_id: string
  scope: string
  openid: string
}

/** 注册请求体（继承登录体，clientId/grantType 必填） */
export interface RegisterBody extends LoginBody {
  userType: string
}

/** 图片验证码返回体 */
export interface CaptchaData {
  captchaEnabled: boolean
  uuid: string
  img: string
}

/** 登录 */
export function login(body: LoginBody): Promise<LoginData> {
  return request.post('/auth/login', body)
}

/** 注册 */
export function register(body: RegisterBody): Promise<void> {
  return request.post('/auth/register', body)
}

/** 退出登录 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}

/** 获取图片验证码 */
export function getCaptcha(): Promise<CaptchaData> {
  return request.get('/auth/code')
}
