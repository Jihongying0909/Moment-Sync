<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getPosts, updatePostStatus } from '../api/admin.js'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import FeedbackState from '../components/FeedbackState.vue'
import PaginationBar from '../components/PaginationBar.vue'
import StatusBadge from '../components/StatusBadge.vue'

const query = reactive({ visibility: '', status: '', page: 1, pageSize: 10 })
const result = ref({ items: [], total: 0, page: 1, pageSize: 10 })
const loading = ref(false)
const error = ref('')
const selected = ref(null)
const updating = ref(false)
const toast = ref('')

async function load(page = query.page) {
  loading.value = true; error.value = ''; query.page = page
  try { result.value = await getPosts(query) } catch (err) { error.value = err.message } finally { loading.value = false }
}
function search() { load(1) }
async function offline() {
  if (!selected.value) return
  updating.value = true
  try { await updatePostStatus(selected.value.id, 'OFFLINE'); toast.value = '内容已下架'; selected.value = null; await load() }
  catch (err) { error.value = err.message } finally { updating.value = false }
}
onMounted(() => load())
</script>

<template>
  <section>
    <div class="section-heading"><div><h2>内容管理</h2><p>查看公开印记并处理需要下架的内容</p></div></div>
    <p v-if="toast" class="notice notice--success">{{ toast }}</p>
    <form class="filter-bar" @submit.prevent="search"><select v-model="query.visibility"><option value="">全部可见范围</option><option value="PUBLIC">公开</option><option value="PRIVATE">私密</option></select><select v-model="query.status"><option value="">全部状态</option><option value="PUBLISHED">已发布</option><option value="OFFLINE">已下架</option></select><button class="primary-button" type="submit" :disabled="loading">查询</button></form>
    <FeedbackState v-if="error" title="内容列表加载失败" :description="error" action-label="重新加载" @action="load()" />
    <div v-else class="table-card"><div class="table-scroll"><table><thead><tr><th>印记</th><th>发布者</th><th>可见范围</th><th>状态</th><th>发布时间</th><th>操作</th></tr></thead>
      <tbody><tr v-if="loading"><td colspan="6" class="table-message">正在加载内容数据…</td></tr><tr v-else-if="!result.items.length"><td colspan="6" class="table-message">暂无符合条件的内容</td></tr>
        <tr v-for="post in result.items" :key="post.id"><td class="post-cell"><strong>{{ post.title || '无标题印记' }}</strong><small>ID {{ post.id }}</small></td><td>{{ post.author.nickname || post.author.id || '—' }}</td><td><StatusBadge :value="post.visibility" /></td><td><StatusBadge :value="post.status" /></td><td>{{ post.createdAt || '—' }}</td><td class="actions"><RouterLink class="table-link" :to="`/posts/${post.id}`">详情</RouterLink><button v-if="post.status === 'PUBLISHED'" class="table-link table-link--danger" type="button" @click="selected = post">下架</button></td></tr>
      </tbody></table></div><PaginationBar :page="query.page" :page-size="query.pageSize" :total="result.total" :busy="loading" @change="load" /></div>
    <ConfirmDialog :open="Boolean(selected)" title="确认下架内容" :message="`下架“${selected?.title || '该印记'}”后，小程序将无法继续公开访问。`" confirm-text="确认下架" :busy="updating" @cancel="selected = null" @confirm="offline" />
  </section>
</template>
