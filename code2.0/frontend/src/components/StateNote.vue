<template>
  <p class="note" :class="{ 'is-error': error }" role="status">
    <span><slot /></span>
    <button v-if="error && onRetry" class="retry" type="button" @click="onRetry">重试</button>
  </p>
</template>

<script setup>
defineProps({
  error: { type: Boolean, default: false },
  onRetry: { type: Function, default: null }
})
</script>

<style scoped>
.note {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 8px 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--ink-3);
}

.note.is-error {
  color: #d98b7c;
}

.retry {
  padding-bottom: 2px;
  border-bottom: 1px solid currentColor;
  font-size: 12px;
  letter-spacing: 0.16em;
  color: inherit;
}
</style>
