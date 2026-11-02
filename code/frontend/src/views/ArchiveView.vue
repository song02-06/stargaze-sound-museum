<template>
  <div class="archive">
    <AppTopBar>
      <template #left>
        <RouterLink to="/collection">
          <AppIcon name="chevron-left" :size="13" />回到馆藏
        </RouterLink>
      </template>
      <template #right>
        <RouterLink class="is-brass" to="/archive">
          历史之声专区<AppIcon name="chevron-right" :size="13" />
        </RouterLink>
      </template>
    </AppTopBar>

    <div v-if="loading" class="missing rail">
      <p class="serif">正在调取档案…</p>
    </div>

    <div v-else-if="error" class="missing rail">
      <p class="serif">{{ error }}</p>
      <button class="btn" type="button" @click="run">再试一次</button>
    </div>

    <template v-else-if="item">
      <p class="tag">彩蛋 · 历史之声</p>

      <article class="card">
        <div class="row1">
          <span>{{ item.archive }}</span>
          <span class="stamp">馆藏</span>
        </div>
        <h1 class="serif">{{ item.title }}</h1>
        <div class="rule" />
        <p class="body serif">{{ item.era }}<br />{{ item.note }}</p>
        <p class="source">{{ item.source }} · {{ item.license }}</p>
      </article>

      <section class="player">
        <WaveTrace warm :trace="item.trace" :progress="progress" :label="`${item.title} 的声波`" />
        <div class="controls">
          <button class="play" type="button" :aria-label="playing ? '暂停' : '播放'" @click="toggle">
            <AppIcon :name="playing ? 'pause' : 'play'" :size="15" />
          </button>
          <span class="time tnum">{{ clock(current) }} / {{ clock(duration || item.seconds) }}</span>
        </div>
      </section>

      <!-- 捞上来的那一句只在捞上来时说。从专区点进来的这几段是有目录的。 -->
      <footer class="foot">
        <p v-if="fromDraw" class="caption serif">你捞到了一段历史之声</p>
        <button class="btn quiet draw-again" type="button" :disabled="drawing" @click="draw()">
          <AppIcon name="draw" :size="14" />再打捞一段
        </button>
      </footer>

      <audio :ref="bind" :src="item.audioUrl" preload="auto" />
    </template>
  </div>
</template>

<script setup>
import { computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import WaveTrace from '../components/WaveTrace.vue'
import AppIcon from '../components/AppIcon.vue'
import { useAsync } from '../composables/useAsync.js'
import { useDraw } from '../composables/useDraw.js'
import * as api from '../api/index.js'
import { usePlayer } from '../composables/usePlayer.js'
import { clock } from '../lib/format.js'

const route = useRoute()
const { data: item, loading, error, run } = useAsync(() => api.heritageOne(route.params.id))

const { bind, reset, playing, current, duration, progress, toggle, play } = usePlayer()
const { drawing, seq, last, draw } = useDraw()

const fromDraw = computed(() => route.query.from === 'draw')

let timer = 0

// 历史之声晚一拍开始播：先让视线落在卡片上，再出声
async function playSoon() {
  window.clearTimeout(timer)
  reset()
  await nextTick()
  timer = window.setTimeout(() => play(), 420)
}

watch(item, (value) => {
  if (value) playSoon()
})

// 连着捞：参数变了但组件被复用，不重新取数就会留着上一条档案
watch(
  () => route.params.id,
  () => run()
)

// 又捞到同一段：参数没变，声音要重新起一次。
// 捞到的是别的一段时不在这里抢着播 —— 那一段有自己的取数与起播。
watch(seq, () => {
  if (last.value?.kind === 'heritage' && last.value.key === route.params.id && item.value) {
    playSoon()
  }
})

onUnmounted(() => window.clearTimeout(timer))
</script>

<style scoped>
.archive {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.tag {
  margin-top: 26px;
  text-align: center;
  font-size: 11px;
  letter-spacing: 0.42em;
  text-indent: 0.42em;
  color: rgba(201, 169, 107, 0.88);
}

.card {
  position: relative;
  width: min(100% - var(--rail) * 2, 640px);
  margin: 34px auto 0;
  padding: 44px 52px;
  background: linear-gradient(168deg, var(--paper) 0%, var(--paper-2) 58%, var(--paper-3) 100%);
  box-shadow: 0 22px 36px -20px rgba(0, 0, 0, 0.85);
  transform: rotate(-0.35deg);
}

.row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--paper-ink-2);
}

.stamp {
  flex: none;
  padding: 5px 10px;
  border: 1px solid rgba(151, 64, 47, 0.55);
  font-family: var(--sans);
  font-size: 11px;
  letter-spacing: 0.24em;
  color: var(--stamp);
  transform: rotate(4deg);
}

.card h1 {
  margin-top: 30px;
  font-size: clamp(24px, 2.6vw, 31px);
  font-weight: 500;
  letter-spacing: 0.05em;
  color: var(--paper-ink);
}

.rule {
  height: 1px;
  margin: 26px 0 24px;
  background: rgba(80, 66, 42, 0.28);
}

.body {
  font-size: 15px;
  font-weight: 400;
  line-height: 2.1;
  color: #4c4029;
}

.source {
  margin-top: 34px;
  font-size: 11px;
  letter-spacing: 0.20em;
  color: var(--paper-ink-2);
}

.player {
  width: min(100% - var(--rail) * 2, 560px);
  margin: 46px auto 0;
}

.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-top: 26px;
}

.play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(214, 190, 144, 0.28);
  border-radius: 50%;
  background: rgba(214, 190, 144, 0.05);
  transition: border-color var(--dur-fast) var(--ease);
}

.play:hover {
  border-color: rgba(214, 190, 144, 0.6);
}

.play {
  color: #d8bb84;
}

.time {
  font-family: var(--mono);
  font-size: 11px;
  color: #a8946a;
}

.foot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  margin-top: auto;
  padding: 54px var(--rail) 46px;
}

.caption {
  text-align: center;
  font-size: 13px;
  font-weight: 300;
  letter-spacing: 0.30em;
  text-indent: 0.30em;
  color: rgba(201, 169, 107, 0.68);
}

.missing {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 26px;
  color: var(--ink-2);
}

@media (max-width: 760px) {
  .card {
    padding: 30px 24px;
  }

  .card h1 {
    margin-top: 22px;
  }
}
</style>
