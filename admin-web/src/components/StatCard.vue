<script setup>
defineProps({ label: String, value: [Number, String], tone: { type: String, default: 'blue' } })
</script>

<template>
  <article class="stat-card" :class="`stat-card--${tone}`">
    <span>{{ label }}</span>
    <strong>{{ value ?? '—' }}</strong>
  </article>
</template>
