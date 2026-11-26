<script setup>
import { onMounted, ref } from 'vue'
import { getStatistics } from '../api/admin.js'
import FeedbackState from '../components/FeedbackState.vue'
import StatCard from '../components/StatCard.vue'

const stats = ref(null)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true; error.value = ''
  try { stats.value = await getStatistics() } catch (err) { error.value = err.message } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="section-heading"><div><h2>平台数据概览</h2><p>快速了解当前用户、印记和互动规模</p></div><button class="secondary-button" type="button" :disabled="loading" @click="load">刷新数据</button></div>
    <div v-if="loading" class="stat-grid"><StatCard v-for="item in 3" :key="item" label="正在加载" value="—" /></div>
    <FeedbackState v-else-if="error" title="统计数据加载失败" :description="error" action-label="重新加载" @action="load" />
    <div v-else class="stat-grid">
      <StatCard label="用户总数" :value="stats.userCount" tone="blue" />
      <StatCard label="印记总数" :value="stats.postCount" tone="violet" />
      <StatCard label="评论总数" :value="stats.commentCount" tone="green" />
    </div>
    <article class="content-card progress-card"><div><span class="eyebrow">内容治理</span><h3>校园印记审核与下架</h3><p>支持查看平台公开与私密印记，下架内容后小程序首页、搜索、详情及评论入口将立即同步隐藏或拦截。</p></div><RouterLink class="primary-button" to="/posts">进入内容管理</RouterLink></article>
  </section>
</template>
