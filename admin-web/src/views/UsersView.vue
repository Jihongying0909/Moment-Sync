<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getUsers } from '../api/admin.js'
import FeedbackState from '../components/FeedbackState.vue'
import PaginationBar from '../components/PaginationBar.vue'
import StatusBadge from '../components/StatusBadge.vue'

const query = reactive({ keyword: '', status: '', page: 1, pageSize: 10 })
const result = ref({ items: [], total: 0, page: 1, pageSize: 10 })
const loading = ref(false)
const error = ref('')

async function load(page = query.page) {
  loading.value = true; error.value = ''; query.page = page
  try { result.value = await getUsers(query) } catch (err) { error.value = err.message } finally { loading.value = false }
}
function search() { load(1) }
onMounted(() => load())
</script>

<template>
  <section>
    <div class="section-heading"><div><h2>用户管理</h2><p>查看平台注册用户及账号状态</p></div></div>
    <form class="filter-bar" @submit.prevent="search"><input v-model="query.keyword" placeholder="搜索账号或昵称" /><select v-model="query.status"><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">已禁用</option></select><button class="primary-button" type="submit" :disabled="loading">查询</button></form>
    <FeedbackState v-if="error" title="用户列表加载失败" :description="error" action-label="重新加载" @action="load()" />
    <div v-else class="table-card">
      <div class="table-scroll"><table><thead><tr><th>用户</th><th>账号</th><th>角色</th><th>状态</th><th>注册时间</th></tr></thead>
        <tbody><tr v-if="loading"><td colspan="5" class="table-message">正在加载用户数据…</td></tr><tr v-else-if="!result.items.length"><td colspan="5" class="table-message">暂无用户数据</td></tr>
          <tr v-for="user in result.items" :key="user.id"><td><strong>{{ user.nickname || '未设置昵称' }}</strong><small>ID {{ user.id }}</small></td><td>{{ user.username || '—' }}</td><td><StatusBadge :value="user.role" /></td><td><StatusBadge :value="user.status" /></td><td>{{ user.createdAt || '—' }}</td></tr>
        </tbody></table></div>
      <PaginationBar :page="query.page" :page-size="query.pageSize" :total="result.total" :busy="loading" @change="load" />
    </div>
  </section>
</template>
