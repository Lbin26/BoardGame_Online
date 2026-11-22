import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  request: { get: vi.fn(), put: vi.fn(), post: vi.fn() },
  TOKEN_KEY: 'access_token',
  getToken: vi.fn(() => ''),
}))

import { request } from '@/utils/request'
import { getProfile, updateProfile, uploadAvatar } from '@/api/user'

const get = vi.mocked(request.get)
const put = vi.mocked(request.put)
const post = vi.mocked(request.post)

beforeEach(() => {
  get.mockReset()
  put.mockReset()
  post.mockReset()
})

describe('api/user', () => {
  it('getProfile → GET /system/user/profile', async () => {
    get.mockResolvedValue({ user: {} })
    await getProfile()
    expect(get).toHaveBeenCalledWith('/system/user/profile')
  })

  it('updateProfile → PUT /system/user/profile，含 avatar 字段', async () => {
    put.mockResolvedValue(undefined)
    const body = { nickName: '张三', avatar: 123 }
    await updateProfile(body)
    expect(put).toHaveBeenCalledWith('/system/user/profile', body)
  })

  it('uploadAvatar → POST /api/avatar/upload，FormData 带 file，超时放宽到 15s', async () => {
    post.mockResolvedValue({ ossId: 123, url: '/api/avatar/123' })
    const file = new File(['abc'], 'avatar.png', { type: 'image/png' })
    const result = await uploadAvatar(file)

    expect(post).toHaveBeenCalledTimes(1)
    const [url, formData, config] = post.mock.calls[0]
    expect(url).toBe('/api/avatar/upload')
    expect(formData).toBeInstanceOf(FormData)
    expect((formData as FormData).get('file')).toBe(file)
    expect(config).toMatchObject({
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 15000,
    })
    expect(result).toEqual({ ossId: 123, url: '/api/avatar/123' })
  })
})
