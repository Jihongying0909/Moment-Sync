<script setup>
import { computed } from 'vue'
const props = defineProps({ page: Number, pageSize: Number, total: Number, busy: Boolean })
const emit = defineEmits(['change'])
const pageCount = computed(() => Math.max(1, Math.ceil((props.total || 0) / (props.pageSize || 10))))
</script>

<template>
  <div class="pagination-bar">
    <span>共 {{ total }} 条，第 {{ page }} / {{ pageCount }} 页</span>
    <div>
      <button class="secondary-button" type="button" :disabled="busy || page <= 1" @click="emit('change', page - 1)">上一页</button>
      <button class="secondary-button" type="button" :disabled="busy || page >= pageCount" @click="emit('change', page + 1)">下一页</button>
    </div>
  </div>
</template>
