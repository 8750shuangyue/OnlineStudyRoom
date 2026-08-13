const TOKEN_KEY = 'study_room_token'
const REFRESH_KEY = 'study_room_refresh_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(token, refreshToken) {
  setToken(token)
  if (refreshToken) {
    localStorage.setItem(REFRESH_KEY, refreshToken)
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  try {
    const resp = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    })
    if (!resp.ok) {
      return false
    }
    const data = await resp.json()
    setTokens(data.token, data.refreshToken)
    return true
  } catch {
    return false
  }
}

/**
 * 统一请求封装：自动带 JWT，401 时清除登录态并跳回登录页。
 */
export async function api(path, { method = 'GET', body, auth = true, retried = false } = {}) {
  const headers = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (auth) {
    const token = getToken()
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  const resp = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  })

  if (resp.status === 429 && !retried) {
    await new Promise((r) => setTimeout(r, 2000))
    return api(path, { method, body, auth, retried: true })
  }

  if (resp.status === 401 && auth) {
    if (!retried && (await refreshAccessToken())) {
      return api(path, { method, body, auth, retried: true })
    }
    clearTokens()
    window.location.href = '/login'
    throw new Error('未登录或登录已过期')
  }

  if (!resp.ok) {
    let message = `请求失败（HTTP ${resp.status}）`
    try {
      const data = await resp.json()
      if (data.message) message = data.message
      else if (data.error) message = data.error
    } catch {
      // 非 JSON 错误体，保留通用提示
    }
    throw new Error(message)
  }

  if (resp.status === 204) {
    return null
  }
  const text = await resp.text()
  // 空响应体（如无进行中会话时返回 null 的接口）按 null 处理
  return text ? JSON.parse(text) : null
}
