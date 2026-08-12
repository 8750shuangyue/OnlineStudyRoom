import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, setTokens } from '../api.js'

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

describe('api()', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('携带 Bearer token 并解析 JSON', async () => {
    setTokens('token123')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    const data = await api('/api/rooms')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rooms',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer token123' })
      })
    )
    expect(data).toEqual({ id: 1 })
  })

  it('从错误响应中提取 message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: '房间不存在' }, 404)))

    await expect(api('/api/rooms/999')).rejects.toThrow('房间不存在')
  })

  it('401 时刷新 token 并自动重试一次', async () => {
    setTokens('old', 'refresh123')
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ token: 'new', refreshToken: 'newrefresh' }))
      .mockResolvedValueOnce(jsonResponse({ username: 'alice' }))
    vi.stubGlobal('fetch', fetchMock)

    const data = await api('/api/auth/me')

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(data).toEqual({ username: 'alice' })
    expect(localStorage.getItem('study_room_token')).toBe('new')
  })

  it('GET 无 token 时不带 Authorization', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await api('/api/rooms')

    const headers = fetchMock.mock.calls[0][1].headers
    expect(headers.Authorization).toBeUndefined()
  })
})
