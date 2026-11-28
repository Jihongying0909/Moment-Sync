<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '../components/BrandLogo.vue'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const expired = computed(() => route.query.reason === 'expired')

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) { error.value = '请输入管理员账号和密码'; return }
  busy.value = true
  try {
    await auth.login({ username: username.value.trim(), password: password.value })
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (err) {
    error.value = err?.message || '登录失败，请稍后重试'
  } finally { busy.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <span class="eyebrow">Moment Sync</span>
      <h1>让每一段<span class="marker">校园记忆</span><br />都有清晰的守护</h1>
      <p>拾光同频管理后台用于查看平台运行情况、管理用户与审核公开内容。</p>
    </section>
    <section class="login-card">
      <div class="login-card__heading"><BrandLogo :size="50" /><div><h2>管理员登录</h2><p>请使用管理员账号进入后台</p></div></div>
      <p v-if="expired" class="notice notice--warning">登录状态已失效，请重新登录。</p>
      <form @submit.prevent="submit">
        <label>账号<input v-model="username" name="username" autocomplete="username" placeholder="请输入管理员账号" /></label>
        <label>密码<input v-model="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="primary-button primary-button--wide" type="submit" :disabled="busy">{{ busy ? '正在登录…' : '登录后台' }}</button>
      </form>
      <p class="login-card__hint">普通用户账号无法进入管理后台</p>
    </section>
  </main>
</template>
