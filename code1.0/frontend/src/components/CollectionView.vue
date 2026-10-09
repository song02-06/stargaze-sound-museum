<template>
  <div class="collection">
    <header class="section-head">
      <h2 class="display">馆藏</h2>
      <p class="lede">
        已经沉入星空的声音。每一条都经过「规则 + AI + 人工」三路审核才会出现在这里。
      </p>
    </header>

    <p v-if="loading" class="state">正在整理目录…</p>
    <p v-else-if="!items.length" class="state">
      馆藏还是空的 —— 去「留一段」放进第一段声音吧。
    </p>

    <div v-else class="grid">
      <button
        v-for="item in items"
        :key="item.id"
        class="item glass"
        @click="$emit('select', item)"
      >
        <span class="emotion" :class="{ muted: !item.emotion }">{{ item.emotion || '未标注' }}</span>
        <h3>{{ item.title }}</h3>
        <p class="excerpt">{{ excerpt(item) }}</p>
        <footer>
          <span v-for="tag in splitTags(item.tags)" :key="tag" class="chip">{{ tag }}</span>
          <time>{{ shortDate(item.createdAt) }}</time>
        </footer>
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

defineEmits(['select'])

function excerpt(item) {
  const text = item.polishedText || item.rawTranscript || ''
  return text.length > 74 ? `${text.slice(0, 74)}…` : text
}

function splitTags(tags) {
  if (!tags) return []
  return String(tags).split(',').map((t) => t.trim()).filter(Boolean).slice(0, 3)
}

function shortDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}月${d.getDate()}日`
}
</script>

<style scoped>
.collection {
  width: min(100%, 1040px);
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-head .display {
  font-size: clamp(24px, 3vw, 34px);
}

.state {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  color: var(--ink-3);
  font-size: 14px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 22px;
  text-align: left;
  border-radius: var(--r-md);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.item:hover {
  transform: translateY(-4px);
  border-color: var(--line-strong);
  box-shadow: 0 22px 50px rgba(0, 0, 0, 0.5);
}

.emotion {
  align-self: flex-start;
  padding: 3px 11px;
  border-radius: var(--r-pill);
  font-size: 11px;
  color: var(--warm);
  background: var(--warm-dim);
  border: 1px solid rgba(255, 207, 153, 0.24);
}

.emotion.muted {
  color: var(--ink-3);
  background: transparent;
  border-color: var(--line);
}

.item h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 500;
  line-height: 1.5;
}

.excerpt {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-3);
}

.item footer {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}

.item footer .chip {
  font-size: 11px;
  padding: 2px 9px;
}

.item time {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
}
</style>
