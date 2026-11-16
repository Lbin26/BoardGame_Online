import axios, {
  type AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'

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
 * 仅 auth / user 等返回 `R` 的接口使用；象棋接口返回裸 Map，不走此函数。
 */
export function unwrapR(body: any): any {
  if (body?.code === 200) {
    return body.data
  }
  throw new Error(body?.msg ?? '请求失败')
}

/** 请求拦截器：注入 `Authorization: Bearer <token>` */
function attachAuthToken(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

/** 401 处理：清除本地令牌并跳转登录页 */
function handleUnauthorized(): void {
  localStorage.removeItem(TOKEN_KEY)
  window.location.href = '/login'
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

request.interceptors.response.use(
  (response) => unwrapR(response.data),
  onResponseError,
)

/**
 * 原始请求实例：用于象棋接口（返回裸 Map，非 `R` 信封）。
 * 响应拦截器直接返回 `res.data`，不做解包。
 */
export const rawRequest: AxiosInstance = axios.create({
  timeout: 10000,
})

rawRequest.interceptors.request.use(attachAuthToken)

rawRequest.interceptors.response.use(
  (response) => response.data,
  onResponseError,
)

export default request
