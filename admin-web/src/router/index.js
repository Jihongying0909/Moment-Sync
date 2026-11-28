import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import PostDetailView from '../views/PostDetailView.vue'
import PostsView from '../views/PostsView.vue'
import UsersView from '../views/UsersView.vue'
import { ADMIN_TOKEN_KEY } from '../config.js'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', name: 'dashboard', component: DashboardView, meta: { title: '数据概览' } },
        { path: 'users', name: 'users', component: UsersView, meta: { title: '用户管理' } },
        { path: 'posts', name: 'posts', component: PostsView, meta: { title: '内容管理' } },
        { path: 'posts/:id', name: 'post-detail', component: PostDetailView, meta: { title: '印记详情' } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { public: true } },
  ],
})

router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem(ADMIN_TOKEN_KEY))
  if (!to.meta.public && !hasToken) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && hasToken) return { name: 'dashboard' }
  return true
})

export default router
