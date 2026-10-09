<template>
  <article class="card glass" :class="{ heritage: isHeritage }">
    <div class="head">
      <span class="kind" :class="isHeritage ? 'warm' : 'cool'">
        {{ isHeritage ? '历史声音彩蛋' : '陌生人的留言' }}
      </span>
      <button class="close" aria-label="收起" @click="$emit('close')">✕</button>
    </div>

    <h2 class="display title">{{ item.title }}</h2>

    <p class="story">{{ item.story }}</p>

    <div v-if="tags.length || item.emotion" class="chips">
      <span v-if="item.emotion" class="chip warm">{{ item.emotion }}</span>
      <span v-for="tag in tags" :key="tag" class="chip">{{ tag }}</span>
    </div>

    <dl v-if="isHeritage" class="meta">
      <div><dt>人物</dt><dd>{{ item.speaker || '—' }}</dd></div>
      <div><dt>年代</dt><dd>{{ item.era || '—' }}</dd></div>
      <div><dt>国别</dt><dd>{{ item.country || '—' }}</dd></div>
      <div><dt>来源</dt><dd>{{ item.sourceName || '—' }}</dd></div>
      <div class="wide"><dt>授权</dt><dd>{{ item.licenseNote || '—' }}</dd></div>
    </dl>

    <AudioPlayer :src="item.audioUrl" @level="$emit('level', $event)" />

    <div class="actions">
      <button class="btn primary" @click="$emit('next')">再捞一段</button>
      <button class="btn" @click="$emit('close')">沉回星空</button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import AudioPlayer from './AudioPlayer.vue'

const props = defineProps({
  item: { type: Object, required: true }
})

defineEmits(['close', 'next', 'level'])

const isHeritage = computed(() => props.item.type === 'HERITAGE')

const tags = computed(() => {
  const raw = props.item.tags
  if (!raw) return []
  return String(raw).split(',').map((t) => t.trim()).filter(Boolean)
})
</script>

<style scoped>
.card {
  width: min(100%, 620px);
  padding: clamp(24px, 3vw, 38px);
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: relative;
  overflow: hidden;
}

/* 彩蛋用一道暖色光边区分，不靠改整块底色 */
.card.heritage {
  border-color: rgba(255, 207, 153, 0.28);
}

.card.heritage::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(ellipse 60% 40% at 80% 0%, rgba(255, 207, 153, 0.12), transparent 70%);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kind {
  padding: 5px 14px;
  border-radius: var(--r-pill);
  font-size: 11px;
  letter-spacing: 0.14em;
}

.close {
  color: var(--ink-3);
  font-size: 14px;
  padding: 4px 8px;
  transition: color 0.2s ease;
}

.close:hover {
  color: var(--ink);
}

.title {
  font-size: clamp(22px, 2.6vw, 30px);
  margin: 0;
}

.story {
  margin: 0;
  font-size: 15px;
  line-height: 2.05;
  color: var(--ink-2);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  margin: 0;
  padding: 16px 18px;
  border-radius: var(--r-md);
  background: rgba(255, 207, 153, 0.05);
  border: 1px solid rgba(255, 207, 153, 0.14);
  font-size: 12.5px;
}

.meta > div {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.meta .wide {
  grid-column: 1 / -1;
}

.meta dt {
  color: var(--ink-3);
  flex: none;
}

.meta dd {
  margin: 0;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.actions .btn {
  flex: 1;
  min-width: 130px;
}
</style>
