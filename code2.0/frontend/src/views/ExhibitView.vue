<template>
  <div class="exhibit">
    <AppTopBar>
      <template #left>
        <RouterLink to="/collection">
          <AppIcon name="chevron-left" :size="13" />回到馆藏
        </RouterLink>
      </template>
      <template #right>
        <RouterLink to="/mine">我的声音</RouterLink>
      </template>
    </AppTopBar>

    <div v-if="loading" class="missing rail">
      <p class="serif">正在把这一段提上来…</p>
    </div>

    <div v-else-if="error" class="missing rail">
      <p class="serif">{{ error }}</p>
      <button class="btn" type="button" @click="run">再试一次</button>
    </div>

    <template v-if="exhibit">
      <section class="stage-area">
        <div class="baseline" aria-hidden="true" />
        <WaveTrace
          class="trace"
          :trace="exhibit.trace"
          :progress="progress"
          :label="`${exhibit.title} 的声波`"
        />
      </section>

      <!-- 故事要比声音晚出现 1–2 秒。
           但如果浏览器拦下了自动播放，立刻显示 —— 内容不能因为自动播放策略而消失。 -->
      <section
        ref="plaqueEl"
        class="plaque-area rail"
        :class="{ revealed }"
        tabindex="-1"
      >
        <p v-if="fromDraw" class="caught">你捞到了这一段</p>
        <ExhibitPlaque :exhibit="exhibit" />
      </section>

      <footer class="foot rail">
        <div class="left">
          <button class="play" type="button" :aria-label="playing ? '暂停' : '播放'" @click="toggle">
            <AppIcon :name="playing ? 'pause' : 'play'" :size="15" />
          </button>
          <span class="time tnum">{{ clock(current) }} / {{ clock(duration || exhibit.seconds) }}</span>
          <span v-if="failed" class="warn">这段音频没加载出来</span>
          <button class="btn quiet draw-again" type="button" :disabled="drawing" @click="draw()">
            <AppIcon name="draw" :size="14" />再打捞一段
          </button>
        </div>
        <RouterLink class="btn primary" :to="`/exhibit/${exhibit.no}/echo`">留下回音</RouterLink>
        <p class="notice">演示音频：合成占位素材，不是真实录音</p>
      </footer>

      <audio :ref="bind" :src="exhibit.audioUrl" preload="auto" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import WaveTrace from '../components/WaveTrace.vue'
import ExhibitPlaque from '../components/ExhibitPlaque.vue'
import AppIcon from '../components/AppIcon.vue'
import { useAsync } from '../composables/useAsync.js'
import { useDraw } from '../composables/useDraw.js'
import * as api from '../api/index.js'
import { usePlayer } from '../composables/usePlayer.js'
import { clock } from '../lib/format.js'

const route = useRoute()
const { data: exhibit, loading, error, run } = useAsync(() => api.exhibit(route.params.id))

const { bind, reset, playing, current, duration, progress, blocked, failed, toggle, play } =
  usePlayer()

const { drawing, seq, last, draw } = useDraw()

/** 只有捞上来的那一次才说「你捞到了」；从馆藏点进来是无主的。 */
const fromDraw = computed(() => route.query.from === 'draw')

const revealed = ref(false)
const plaqueEl = ref(null)
let revealTimer = 0
let safetyTimer = 0

// 声音先于文字：数据到了、音频真的开始播了，故事再过 1.3 秒出现。
// 但如果浏览器拦下了自动播放，立刻显示 —— 内容不能因为自动播放策略而消失。
async function reveal() {
  window.clearTimeout(revealTimer)
  window.clearTimeout(safetyTimer)
  revealed.value = false
  reset()
  await nextTick()
  await play()

  if (blocked.value || failed.value) {
    revealed.value = true
    return
  }

  revealTimer = window.setTimeout(() => {
    revealed.value = true
  }, 1300)

  // 兜底：不管发生什么，2.6 秒后故事都必须出现
  safetyTimer = window.setTimeout(() => {
    revealed.value = true
  }, 2600)
}

watch(exhibit, (value) => {
  if (value) reveal()
})

// 连着捞：路由参数变了但组件被复用，不重新取数就会留着上一条展品
watch(
  () => route.params.id,
  () => {
    revealed.value = false
    run()
  }
)

// 又捞到同一件（池子小的时候会发生）：参数没变，但亮相要重演一遍。
// 捞到的是别的一条时不在这里重演 —— 那一条有自己的取数与亮相。
watch(seq, () => {
  if (last.value?.kind === 'exhibit' && last.value.key === route.params.id && exhibit.value) {
    reveal()
  }
})

// 幕布抬起、展签露面时，把焦点交给它 —— 键盘用户要知道自己落在哪儿。
// preventScroll：不打断「声音先于文字」的滚动位置。
watch(revealed, (on) => {
  if (on && fromDraw.value) plaqueEl.value?.focus({ preventScroll: true })
})

onUnmounted(() => {
  window.clearTimeout(revealTimer)
  window.clearTimeout(safetyTimer)
})
</script>

<style scoped>
.exhibit {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
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

.stage-area {
  position: relative;
  flex: none;
  padding: 78px var(--rail) 0;
}

/* 声波的中轴线 —— 让痕迹读起来像一次测量，而不是一段装饰 */
.baseline {
  position: absolute;
  left: 12%;
  right: 12%;
  top: calc(78px + 66px);
  height: 1px;
  background: linear-gradient(
    to right,
    transparent,
    rgba(200, 218, 255, 0.16) 18%,
    rgba(200, 218, 255, 0.16) 82%,
    transparent
  );
}

.trace {
  position: relative;
  max-width: 720px;
  margin: 0 auto;
}

.plaque-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28px;
  padding-top: 52px;
  opacity: 0;
  transform: translateY(14px);
  transition: opacity var(--dur-slow) var(--ease), transform var(--dur-slow) var(--ease);
}

.plaque-area.revealed {
  opacity: 1;
  transform: none;
}

/* 焦点只是为了让人知道落在哪儿，不必在整块展签外面画一个圈 */
.plaque-area:focus {
  outline: none;
}

/* 捞上来的那一次才有的标记。和「你捞到了一段历史之声」同一套字样 */
.caught {
  font-size: 11px;
  letter-spacing: 0.42em;
  text-indent: 0.42em;
  color: rgba(201, 169, 107, 0.88);
}

.foot {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: auto;
  padding-top: 56px;
  padding-bottom: 54px;
}

.foot .left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--line-2);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.015);
  transition: border-color var(--dur-fast) var(--ease);
}

.play:hover {
  border-color: rgba(255, 255, 255, 0.28);
}

.time {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--ink-4);
}

.warn {
  font-size: 12px;
  color: #d98b7c;
}

.notice {
  margin-left: auto;
  font-size: 11px;
  letter-spacing: 0.10em;
  color: var(--ink-4);
}

.fade-enter-active {
  transition: opacity var(--dur) var(--ease);
}

.fade-enter-from {
  opacity: 0;
}

@media (max-width: 760px) {
  .stage-area {
    padding-top: 44px;
  }

  .baseline {
    top: calc(44px + 46px);
  }

  .plaque-area {
    padding-top: 36px;
  }

  .foot {
    flex-wrap: wrap;
    gap: 18px;
    padding-bottom: 36px;
  }

  .foot .left {
    flex-wrap: wrap;
    gap: 14px 18px;
  }

  .notice {
    flex-basis: 100%;
    margin-left: 0;
  }
}
</style>
