import { API_BASE_URL, ADMIN_PROFILE_KEY, ADMIN_TOKEN_KEY } from '../config.js'

export class ApiError extends Error {
  constructor(message, options = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status || 0
    this.code = options.code ?? null
    this.data = options.data ?? null
  }
}

export function getToken() {
  return localStorage.getItem(ADMIN_TOKEN_KEY) || ''
}

export function clearSession() {
  localStorage.removeItem(ADMIN_TOKEN_KEY)
  localStorage.removeItem(ADMIN_PROFILE_KEY)
}

function buildUrl(path, query) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = new URL(`${API_BASE_URL}${normalizedPath}`, window.location.origin)
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  })
  return url.toString()
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method || 'GET',
      headers,
      body:
        options.body === undefined || options.body instanceof FormData
          ? options.body
          : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new ApiError('无法连接服务器，请确认后端已启动', { data: error })
  }

  let payload = null
  const text = await response.text()
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      throw new ApiError('服务器返回了无法解析的数据', { status: response.status })
    }
  }

  const code = payload?.code
  if (!response.ok || (code !== undefined && code !== 0)) {
    if (response.status === 401 || code === 40101) {
      clearSession()
      window.dispatchEvent(new CustomEvent('admin-session-expired'))
    }
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, {
      status: response.status,
      code,
      data: payload?.data,
    })
  }

  return payload?.data ?? payload
}
