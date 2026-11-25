<script setup>
import { computed } from 'vue'

const props = defineProps({ value: { type: String, default: '' } })
const labelMap = { ACTIVE: '正常', DISABLED: '已禁用', PUBLISHED: '已发布', OFFLINE: '已下架', DELETED: '已删除', ADMIN: '管理员', USER: '普通用户', PUBLIC: '公开', PRIVATE: '私密' }
const tone = computed(() => ['ACTIVE', 'PUBLISHED', 'PUBLIC'].includes(props.value) ? 'success' : ['DISABLED', 'OFFLINE', 'DELETED'].includes(props.value) ? 'danger' : 'neutral')
</script>

<template><span class="status-badge" :class="`status-badge--${tone}`">{{ labelMap[value] || value || '未知' }}</span></template>
