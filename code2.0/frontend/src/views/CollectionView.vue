<template>
  <div class="collection">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink class="is-current" to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
        <RouterLink to="/mine">我的声音</RouterLink>
      </template>
    </AppTopBar>

    <header class="head rail">
      <p class="eyebrow">馆藏</p>
      <h1 class="serif">已经沉入星空的声音</h1>
      <p class="lede">
        每一件都有编号、时间、地点，和一段由留下它的人亲手写下的故事。
      </p>
    </header>

    <div class="rail">
      <StateNote v-if="loading">正在整理目录…</StateNote>
      <StateNote v-else-if="error" error :on-retry="run">{{ error }}</StateNote>
      <StateNote v-else-if="!items?.length">馆藏还是空的。</StateNote>
      <ul v-else class="grid">
        <li v-for="item in items" :key="item.id">
          <RouterLink class="card" :to="`/exhibit/${item.no}`">
            <span class="no tnum">Nº {{ item.no }}</span>
            <h2 class="serif">{{ item.title }}</h2>
            <p class="sign">{{ item.sign }}</p>
            <span class="meta">{{ item.place }} · {{ dotDate(item.date) }}</span>
          </RouterLink>
        </li>
      </ul>
    </div>

    <footer class="foot rail">
      <p>{{ assetNotice }}</p>
      <p>回音与用户数据均为演示内容</p>
    </footer>
  </div>
</template>

<script setup>
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import * as api from '../api/index.js'
import { dotDate } from '../lib/format.js'

const { data: items, loading, error, run } = useAsync(() => api.exhibits())

const assetNotice = '演示音频：合成占位素材，不是真实录音'
</script>

<style scoped>
.collection {
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
  color: var(--ink);
}

.lede {
  margin-top: 16px;
  max-width: var(--measure);
  font-size: 14px;
  font-weight: 300;
  line-height: 2;
  color: var(--ink-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  list-style: none;
}

.card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 28px 26px 26px;
  border-top: 1px solid var(--line-2);
  background: linear-gradient(rgba(255, 255, 255, 0.018), transparent 60%);
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.card:hover {
  border-top-color: var(--brass-2);
  background: linear-gradient(rgba(201, 169, 107, 0.05), transparent 60%);
  text-decoration: none;
}

.no {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--brass);
}

.card h2 {
  margin-top: 16px;
  font-size: 19px;
  font-weight: 400;
  letter-spacing: 0.04em;
  color: var(--ink);
}

.sign {
  flex: 1;
  margin-top: 14px;
  font-size: 13px;
  font-weight: 300;
  line-height: 1.95;
  color: var(--ink-2);
  text-wrap: pretty;
}

.meta {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--ink-4);
}

.foot {
  margin-top: auto;
  padding-top: 48px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
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
    gap: 0;
  }
}
</style>
