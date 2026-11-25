<script setup>
defineProps({ title: String, description: String, actionLabel: String })
defineEmits(['action'])
</script>

<template>
  <div class="feedback-state">
    <strong>{{ title }}</strong>
    <p v-if="description">{{ description }}</p>
    <button v-if="actionLabel" class="secondary-button" type="button" @click="$emit('action')">{{ actionLabel }}</button>
  </div>
</template>
