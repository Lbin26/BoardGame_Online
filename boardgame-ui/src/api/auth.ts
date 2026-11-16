import { request } from '@/utils/request'

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

/** 注册请求体 */
export interface RegisterBody {
  username: string
  password: string
  code?: string
  uuid?: string
  userType?: string
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
