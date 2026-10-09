<template>
  <div class="home">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
        <RouterLink to="/mine">我的声音</RouterLink>
      </template>
    </AppTopBar>

    <section class="hero" :class="{ dimmed: drawing }">
      <div class="well" aria-hidden="true"><span /></div>

      <div class="hero-copy">
        <p v-if="loading" class="lede serif">正在数星星…</p>
        <p v-else-if="error" class="lede serif">星星数不清了</p>
        <p v-else class="lede serif">星空里沉了 <b>{{ total }}</b> 段声音</p>
        <p v-if="!loading && !error && heritageCount" class="sub">
          其中 {{ heritageCount }} 段是历史之声
        </p>
        <StateNote v-if="error" error :on-retry="run">{{ error }}</StateNote>
      </div>

      <div class="actions">
        <button class="btn primary" type="button" :disabled="loading || drawing" @click="draw()">
          <AppIcon name="draw" :size="14" />打捞一段
        </button>
        <RouterLink class="btn primary" to="/bury">
          <AppIcon name="bury" :size="14" />埋下一段
        </RouterLink>
      </div>
    </section>

    <section class="recent rail">
      <p class="eyebrow">最近入馆</p>
      <StateNote v-if="recentError" error :on-retry="recentRun">{{ recentError }}</StateNote>
      <ul v-else class="recent-grid">
        <li v-for="item in recentItems" :key="item.id">
          <RouterLink :to="`/exhibit/${item.no}`">
            <h3 class="serif">《{{ item.title }}》</h3>
            <p class="place">{{ item.place }}</p>
            <p class="when">{{ sinceLabel(daysAgo(item.date)) }}</p>
          </RouterLink>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import AppIcon from '../components/AppIcon.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import { useDraw } from '../composables/useDraw.js'
import * as api from '../api/index.js'
import { daysAgo, sinceLabel } from '../lib/format.js'

const { data: stats, loading, error, run } = useAsync(() => api.ping())
const { data: recentItems, error: recentError, run: recentRun } = useAsync(() => api.recent(3))

// 打捞的动效和跳转都挂在全站层（composables/useDraw.js），
// 首页只负责把按钮交出去 —— 否则换页会把这套动效一起卸掉。
const { drawing, draw } = useDraw()

const total = computed(() =>
  stats.value ? stats.value.exhibitCount + stats.value.heritageCount : 0
)
const heritageCount = computed(() => stats.value?.heritageCount || 0)
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.hero {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 30px;
  padding: 40px var(--rail) 0;
  transition: opacity var(--dur) var(--ease);
}

.hero.dimmed {
  opacity: 0;
}

/* 「井口」用同心细环做结构 —— 不用柔光雾。
   柔光雾是最典型的 AI 生成痕迹；同心环读起来像深井，也像打捞时的定位标线。 */
.well {
  position: absolute;
  width: clamp(240px, 33vw, 470px);
  aspect-ratio: 1;
  border: 1px solid rgba(198, 218, 255, 0.13);
  border-radius: 50%;
  pointer-events: none;
}

.well::before,
.well::after,
.well span {
  content: "";
  position: absolute;
  border-radius: 50%;
}

.well::before {
  inset: 14%;
  border: 1px solid rgba(198, 218, 255, 0.095);
}

.well::after {
  inset: 30%;
  border: 1px solid rgba(198, 218, 255, 0.070);
}

.well span {
  inset: 44%;
  border: 1px solid rgba(198, 218, 255, 0.055);
}

.hero-copy {
  position: relative;
  text-align: center;
}

.lede {
  font-size: clamp(22px, 2.3vw, 31px);
  font-weight: 300;
  letter-spacing: 0.10em;
  color: var(--ink-2);
}

.lede b {
  font-size: 1.1em;
  font-weight: 500;
  color: var(--ink);
}

.sub {
  margin-top: 14px;
  font-size: 12px;
  letter-spacing: 0.26em;
  text-indent: 0.26em;
  color: var(--ink-4);
}

.actions {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 26px;
}

.recent {
  padding-bottom: 46px;
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 20px;
  border-top: 1px solid var(--line);
  list-style: none;
}

.recent-grid li + li {
  border-left: 1px solid var(--line);
}

.recent-grid a {
  display: block;
  padding: 20px 30px 4px 0;
}

.recent-grid li:nth-child(2) a,
.recent-grid li:nth-child(3) a {
  padding-left: 30px;
}

.recent-grid a:hover {
  text-decoration: none;
}

.recent-grid h3 {
  font-size: 15px;
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
  transition: color var(--dur-fast) var(--ease);
}

.recent-grid a:hover h3 {
  color: var(--brass);
}

.place {
  margin-top: 9px;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
}

.when {
  margin-top: 7px;
  font-size: 11px;
  letter-spacing: 0.20em;
  color: rgba(201, 169, 107, 0.82);
}

@media (max-width: 760px) {
  .recent-grid {
    grid-template-columns: 1fr;
  }

  .recent-grid li + li {
    border-left: none;
    border-top: 1px solid var(--line);
  }

  .recent-grid a,
  .recent-grid li:nth-child(2) a,
  .recent-grid li:nth-child(3) a {
    padding: 16px 0;
  }

  .actions {
    flex-direction: column;
    gap: 14px;
  }
}
</style>
