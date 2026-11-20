import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import { CLIENT_ID } from '@/constants/client'

/** 登录令牌在 localStorage 中的键名 */
export const TOKEN_KEY = 'access_token'

/** 从 localStorage 读取令牌，未登录返回 null */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 解包后端标准信封 `R<T>`（`{ code, msg, data }`）。
 * 成功约定 `code === 200`，返回 `data`；否则抛出 `Error(msg)`。
 *
 * 纯函数，不做副作用；401 登出跳转由响应拦截器处理。
 */
export function unwrapR(body: any): any {
  if (body?.code === 200) {
    return body.data
  }
  throw new Error(body?.msg ?? '请求失败')
}

/** 请求拦截器：注入 `clientid`（sa-token 必需）与 `Authorization: Bearer <token>` */
export function attachAuthToken(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  config.headers.clientid = CLIENT_ID
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

/** 401 处理：清除本地令牌并跳转登录页（携带回跳地址） */
export function handleUnauthorized(): void {
  localStorage.removeItem(TOKEN_KEY)
  if (window.location.pathname !== '/login') {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
  }
}

/** 检测 R 信封中的 401（HTTP 200 但业务未授权）；命中则登出跳转并返回 true */
export function isEnvelopeUnauthorized(body: any): boolean {
  if (body?.code === 401) {
    handleUnauthorized()
    return true
  }
  return false
}

/** 标准实例响应拦截：先查信封 401，再解包 R */
export function unwrapResponse(response: AxiosResponse): any {
  if (isEnvelopeUnauthorized(response.data)) {
    return Promise.reject(new Error(response.data?.msg ?? '登录已过期'))
  }
  return unwrapR(response.data)
}

/** 原始实例响应拦截：先查信封 401，再透传裸 Map（象棋接口无 code 字段，不受影响） */
export function rawResponse(response: AxiosResponse): any {
  if (isEnvelopeUnauthorized(response.data)) {
    return Promise.reject(new Error(response.data?.msg ?? '登录已过期'))
  }
  return response.data
}

/** 响应错误处理（HTTP 401 时登出并跳转） */
function onResponseError(error: AxiosError): Promise<never> {
  if (error.response?.status === 401) {
    handleUnauthorized()
  }
  return Promise.reject(error)
}

/**
 * 标准请求实例：用于 auth / user 等返回 `R` 信封的接口。
 * 响应拦截器调用 `unwrapR` 解包，直接返回 `data`。
 */
export const request: AxiosInstance = axios.create({
  timeout: 10000,
})

request.interceptors.request.use(attachAuthToken)

request.interceptors.response.use(unwrapResponse, onResponseError)

/**
 * 原始请求实例：用于象棋接口（返回裸 Map，非 `R` 信封）。
 * 响应拦截器直接返回 `res.data`，不做解包。
 */
export const rawRequest: AxiosInstance = axios.create({
  timeout: 10000,
})

rawRequest.interceptors.request.use(attachAuthToken)

rawRequest.interceptors.response.use(rawResponse, onResponseError)

export default request
