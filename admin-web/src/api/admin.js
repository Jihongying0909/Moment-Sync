import { request } from './http.js'
import { normalizePage, normalizePost, normalizeStatistics, normalizeUser } from './normalizers.js'

export async function getStatistics() {
  return normalizeStatistics(await request('/admin/statistics'))
}

export async function getUsers(params = {}) {
  const data = await request('/admin/users', { query: params })
  return normalizePage(data, normalizeUser)
}

export async function getPosts(params = {}) {
  const data = await request('/admin/posts', { query: params })
  return normalizePage(data, normalizePost)
}

export async function getPostDetail(id) {
  return normalizePost(await request(`/admin/posts/${encodeURIComponent(String(id))}`))
}

export async function updatePostStatus(id, status) {
  return request(`/admin/posts/${encodeURIComponent(String(id))}/status`, {
    method: 'PATCH',
    body: { status },
  })
}
