<script setup>
defineProps({ open: Boolean, title: String, message: String, confirmText: { type: String, default: '确认' }, busy: Boolean })
defineEmits(['confirm', 'cancel'])
</script>

<template>
  <div v-if="open" class="dialog-layer" role="presentation" @click.self="$emit('cancel')">
    <section class="dialog" role="dialog" aria-modal="true" :aria-label="title">
      <h2>{{ title }}</h2><p>{{ message }}</p>
      <div class="dialog__actions">
        <button class="secondary-button" type="button" :disabled="busy" @click="$emit('cancel')">取消</button>
        <button class="danger-button" type="button" :disabled="busy" @click="$emit('confirm')">{{ busy ? '处理中…' : confirmText }}</button>
      </div>
    </section>
  </div>
</template>
