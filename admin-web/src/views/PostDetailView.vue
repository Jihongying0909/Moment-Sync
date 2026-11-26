<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostDetail, updatePostStatus } from '../api/admin.js'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import FeedbackState from '../components/FeedbackState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute(); const router = useRouter()
const post = ref(null); const loading = ref(true); const error = ref(''); const actionError = ref(''); const confirmOpen = ref(false); const updating = ref(false)
async function load() { loading.value = true; error.value = ''; try { post.value = await getPostDetail(String(route.params.id)) } catch (err) { error.value = err.message } finally { loading.value = false } }
async function offline() { updating.value = true; actionError.value = ''; try { await updatePostStatus(post.value.id, 'OFFLINE'); confirmOpen.value = false; await load() } catch (err) { actionError.value = err.message; confirmOpen.value = false } finally { updating.value = false } }
onMounted(load)
</script>

<template>
  <section><button class="back-button" type="button" @click="router.push('/posts')">返回内容列表</button>
    <p v-if="actionError" class="notice notice--danger" role="alert">{{ actionError }}</p>
    <FeedbackState v-if="loading" title="正在加载印记详情" />
    <FeedbackState v-else-if="error" title="印记详情加载失败" :description="error" action-label="重新加载" @action="load" />
    <article v-else class="detail-card"><header><div><span class="eyebrow">印记 ID {{ post.id }}</span><h2>{{ post.title || '无标题印记' }}</h2></div><div class="detail-status"><StatusBadge :value="post.visibility" /><StatusBadge :value="post.status" /></div></header>
      <dl class="detail-meta"><div><dt>发布者</dt><dd>{{ post.author.nickname || post.author.id || '—' }}</dd></div><div><dt>发布时间</dt><dd>{{ post.createdAt || '—' }}</dd></div><div><dt>心情</dt><dd>{{ post.mood || '未设置' }}</dd></div><div><dt>地点</dt><dd>{{ post.campusLocation || '未设置' }}</dd></div></dl>
      <img v-if="post.imageUrl" class="detail-image" :src="post.imageUrl" alt="印记图片" /><div v-if="post.tags?.length" class="tag-list"><span v-for="tag in post.tags" :key="tag.id">{{ tag.name }}</span></div><p class="detail-content">{{ post.content || '暂无正文' }}</p>
      <footer v-if="post.status === 'PUBLISHED'"><button class="danger-button" type="button" @click="confirmOpen = true">下架该内容</button></footer>
    </article>
    <ConfirmDialog :open="confirmOpen" title="确认下架内容" message="下架后，小程序首页、搜索、详情和评论接口都应立即拒绝公开访问。" confirm-text="确认下架" :busy="updating" @cancel="confirmOpen = false" @confirm="offline" />
  </section>
</template>
