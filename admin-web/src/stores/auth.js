import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginAdmin } from '../api/auth.js'
import { clearSession } from '../api/http.js'
import { ADMIN_PROFILE_KEY, ADMIN_TOKEN_KEY } from '../config.js'

function readProfile() {
  try {
    return JSON.parse(localStorage.getItem(ADMIN_PROFILE_KEY) || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('admin-auth', () => {
  const token = ref(localStorage.getItem(ADMIN_TOKEN_KEY) || '')
  const profile = ref(readProfile())
  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(credentials) {
    const result = await loginAdmin(credentials)
    const nextToken = result?.token || result?.accessToken || result?.jwt
    if (!nextToken) throw new Error('登录响应缺少 token，请检查后端接口')

    const nextProfile = result?.user || result?.admin || {
      username: credentials.username,
      role: result?.role || 'ADMIN',
    }
    if (nextProfile?.role && nextProfile.role !== 'ADMIN') throw new Error('该账号没有管理员权限')

    token.value = String(nextToken)
    profile.value = nextProfile
    localStorage.setItem(ADMIN_TOKEN_KEY, token.value)
    localStorage.setItem(ADMIN_PROFILE_KEY, JSON.stringify(nextProfile))
  }

  function logout() {
    clearSession()
    token.value = ''
    profile.value = null
  }

  return { token, profile, isAuthenticated, login, logout }
})
