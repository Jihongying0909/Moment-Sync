import { request } from './http.js'

export async function loginAdmin(credentials) {
  return request('/admin/auth/login', { method: 'POST', body: credentials })
}
