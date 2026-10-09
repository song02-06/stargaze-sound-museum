<template>
  <div class="index">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/collection">馆藏</RouterLink>
        <RouterLink class="is-current is-brass" to="/archive">历史之声</RouterLink>
        <RouterLink to="/mine">我的声音</RouterLink>
      </template>
    </AppTopBar>

    <header class="head rail">
      <p class="eyebrow brass">历史之声</p>
      <h1 class="serif">不属于任何人的那几段</h1>
      <p class="lede">
        历史声音不与用户投稿混放。它们有自己的编号、年代和出处，
        将来会换成公有领域的真实录音。
      </p>
    </header>

    <div class="rail">
      <StateNote v-if="loading">正在调取档案…</StateNote>
      <StateNote v-else-if="error" error :on-retry="run">{{ error }}</StateNote>
      <ul v-else class="grid">
        <li v-for="item in items" :key="item.id">
          <RouterLink class="card" :to="`/archive/${item.slug}`">
            <span class="no">{{ item.archiveNo }}</span>
            <h2 class="serif">{{ item.title }}</h2>
            <p class="era">{{ item.era }}</p>
            <p class="note serif">{{ item.note }}</p>
          </RouterLink>
        </li>
      </ul>
    </div>

    <footer class="foot rail">
      <p>全部为演示占位音频 · 上线前须替换为公有领域原作并逐条登记出处</p>
    </footer>
  </div>
</template>

<script setup>
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import * as api from '../api/index.js'

const { data: items, loading, error, run } = useAsync(() => api.heritageList())
</script>

<style scoped>
.index {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  padding-bottom: 40px;
}

.head {
  padding-top: 60px;
  padding-bottom: 44px;
}

.head h1 {
  margin-top: 18px;
  font-size: clamp(24px, 3vw, 36px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: #f0e6cf;
}

.lede {
  margin-top: 16px;
  max-width: var(--measure);
  font-size: 14px;
  font-weight: 300;
  line-height: 2;
  color: #a89a7e;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 22px;
  list-style: none;
}

.card {
  display: block;
  height: 100%;
  padding: 30px 28px;
  background: linear-gradient(168deg, var(--paper) 0%, var(--paper-2) 62%, var(--paper-3) 100%);
  color: var(--paper-ink);
  box-shadow: 0 22px 36px -20px rgba(0, 0, 0, 0.85);
  transition: transform var(--dur) var(--ease);
}

.card:hover {
  transform: translateY(-3px);
  text-decoration: none;
}

.no {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.20em;
  color: var(--paper-ink-2);
}

.card h2 {
  margin-top: 20px;
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 0.04em;
}

.era {
  margin-top: 12px;
  font-size: 12px;
  letter-spacing: 0.10em;
  color: #6a5c40;
}

.note {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid rgba(80, 66, 42, 0.28);
  font-size: 14px;
  font-weight: 400;
  line-height: 2;
  color: #4c4029;
}

.foot {
  margin-top: auto;
  padding-top: 48px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--ink-4);
}

@media (max-width: 760px) {
  .head {
    padding-top: 40px;
    padding-bottom: 32px;
  }

  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
