<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '../components/BrandLogo.vue'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileOpen = ref(false)
const pageTitle = computed(() => route.meta.title || '管理后台')

function logout() {
  auth.logout()
  router.replace({ name: 'login' })
}

function handleExpired() {
  auth.logout()
  router.replace({ name: 'login', query: { reason: 'expired' } })
}

function closeMobile() {
  mobileOpen.value = false
}

onMounted(() => window.addEventListener('admin-session-expired', handleExpired))
onUnmounted(() => window.removeEventListener('admin-session-expired', handleExpired))
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ 'sidebar--open': mobileOpen }">
      <div class="brand">
        <BrandLogo :size="44" />
        <div><strong>拾光同频</strong><span>管理后台</span></div>
      </div>
      <nav class="nav-list" aria-label="后台导航">
        <RouterLink to="/" exact-active-class="is-active" @click="closeMobile">数据概览</RouterLink>
        <RouterLink to="/users" active-class="is-active" @click="closeMobile">用户管理</RouterLink>
        <RouterLink to="/posts" active-class="is-active" @click="closeMobile">内容管理</RouterLink>
      </nav>
      <div class="sidebar__footer">
        <span>{{ auth.profile?.nickname || auth.profile?.username || '管理员' }}</span>
        <button class="text-button" type="button" @click="logout">退出登录</button>
      </div>
    </aside>

    <button v-if="mobileOpen" class="sidebar-backdrop" aria-label="关闭菜单" @click="closeMobile" />

    <main class="main-area">
      <header class="topbar">
        <button class="menu-button" type="button" aria-label="打开菜单" @click="mobileOpen = true">菜单</button>
        <div><p>Moment Sync Admin</p><h1>{{ pageTitle }}</h1></div>
        <span class="environment-badge">Demo</span>
      </header>
      <div class="page-container"><RouterView /></div>
    </main>
  </div>
</template>
