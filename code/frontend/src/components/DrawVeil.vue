<template>
  <!-- 屏幕上唯一在动的东西，就是这一次打捞。 -->
  <Transition name="veil">
    <div v-if="drawing" class="veil">
      <div class="afterglow" />
      <div class="filament" />
      <div class="knot"><span /></div>
      <div class="ripple r1" />
      <div class="ripple r2" />
      <div class="ripple r3" />
      <p class="caption serif" aria-hidden="true">{{ error || '正在星空里打捞' }}</p>
    </div>
  </Transition>

  <!-- 常驻的播报区：幕布是新建出来的，插进去的内容不会被读屏播报，
       所以另留一个一直在的 role=status，只改它的文字。 -->
  <p class="visually-hidden" role="status" aria-live="polite">
    {{ drawing ? error || '正在星空里打捞' : '' }}
  </p>
</template>

<script setup>
import { watch, onMounted, onUnmounted } from 'vue'
import { useDraw } from '../composables/useDraw.js'

const { drawing, error } = useDraw()

/* ---------- 三记声音：垂下 / 勾住 / 提起 ---------- */

/**
 * 打捞是点击触发的，浏览器允许出声。
 *
 * 三记在 2.4 秒内全部收干净：展品自己的声音大约在 2.4 秒（换页之后）起来，
 * 那之后不该再有任何衬底和它抢。所以没有任何持续的衬底音乐。
 */
const HITS = [
  { at: 0, src: '/audio/ritual/descend.wav', volume: 0.5 },
  { at: 800, src: '/audio/ritual/catch.wav', volume: 0.42 },
  { at: 1500, src: '/audio/ritual/lift.wav', volume: 0.34 }
]

const voices = new Map()
let timers = []

function preload() {
  if (reduceMotion()) return
  for (const hit of HITS) {
    const audio = new Audio()
    audio.preload = 'auto'
    audio.src = hit.src
    voices.set(hit.src, audio)
  }
}

function playHits() {
  if (reduceMotion()) return
  timers = HITS.map((hit) =>
    window.setTimeout(() => {
      const audio = voices.get(hit.src)
      if (!audio) return
      audio.currentTime = 0
      audio.volume = hit.volume
      // 自动播放被拦下就算了：声音是注解，不该挡住打捞本身
      audio.play().catch(() => {})
    }, hit.at)
  )
}

function silence() {
  timers.forEach((id) => window.clearTimeout(id))
  timers = []
  for (const audio of voices.values()) {
    audio.pause()
    audio.currentTime = 0
  }
}

/* ---------- 生命周期 ---------- */

watch(drawing, (on) => {
  if (on) playHits()
})

// 没捞上来：这场戏没发生，声音立刻收干净
watch(error, (value) => {
  if (value) silence()
})

onMounted(() => {
  // 别和首屏抢带宽，等页面落定再把这三小段拉下来
  window.setTimeout(preload, 900)
})

onUnmounted(() => {
  silence()
})

const reduceMotion = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches
</script>

<style scoped>
.veil {
  position: fixed;
  inset: 0;
  z-index: var(--z-veil);
  background: var(--void);
}

.afterglow {
  position: absolute;
  left: 50%;
  top: 16%;
  width: 1px;
  height: 46%;
  transform-origin: top center;
  background: linear-gradient(
    to bottom,
    rgba(200, 220, 255, 0),
    rgba(204, 226, 255, 0.10) 34%,
    rgba(216, 234, 255, 0.45) 78%,
    rgba(232, 243, 255, 0.70)
  );
  /* 模糊在这里不是装饰：它把一条线散成「空气里的光」。
     宽度只有 1px、范围只有这一列，不构成第二条光柱。 */
  filter: blur(1.6px);
  opacity: 0;
  animation: afterglow 2000ms var(--ease) 650ms both;
}

.filament {
  position: absolute;
  left: 50%;
  top: 16%;
  width: 1px;
  height: 46%;
  transform-origin: top center;
  background: linear-gradient(
    to bottom,
    rgba(200, 220, 255, 0),
    rgba(200, 220, 255, 0.10) 38%,
    rgba(214, 232, 255, 0.45) 82%,
    rgba(236, 245, 255, 0.95)
  );
  animation: fall 1000ms var(--ease) both;
}

.knot {
  position: absolute;
  left: 50%;
  top: 62%;
  animation: light 900ms var(--ease) 700ms both;
}

.knot span {
  position: absolute;
  left: -34px;
  top: -34px;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: radial-gradient(
    circle at 50% 50%,
    #ffffff 0%,
    rgba(236, 245, 255, 0.72) 9%,
    rgba(178, 208, 250, 0.30) 26%,
    rgba(112, 160, 235, 0.10) 48%,
    transparent 72%
  );
}

.ripple {
  position: absolute;
  left: 50%;
  top: 62%;
  border: 1px solid rgba(190, 215, 255, 0.14);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  animation: ripple 1800ms var(--ease) both;
}

.r1 {
  width: 118px;
  height: 118px;
  animation-delay: 800ms;
}

.r2 {
  width: 210px;
  height: 210px;
  animation-delay: 1000ms;
}

.r3 {
  width: 320px;
  height: 320px;
  animation-delay: 1200ms;
}

.caption {
  position: absolute;
  left: 50%;
  bottom: 12%;
  width: min(86vw, 30rem);
  transform: translateX(-50%);
  text-align: center;
  font-size: 13px;
  font-weight: 300;
  letter-spacing: 0.52em;
  text-indent: 0.52em;
  color: var(--ink-3);
  animation: rise 800ms var(--ease) 500ms both;
}

@keyframes afterglow {
  0% {
    opacity: 0;
    transform: scaleY(0.18);
  }
  32% {
    opacity: 0.45;
    transform: scaleY(0.72);
  }
  56% {
    opacity: 0.34;
    transform: scaleY(1);
  }
  100% {
    opacity: 0.15;
    transform: scaleY(1);
  }
}

@keyframes fall {
  from {
    transform: scaleY(0);
    opacity: 0;
  }
  to {
    transform: scaleY(1);
    opacity: 1;
  }
}

@keyframes light {
  from {
    opacity: 0;
    transform: scale(0.2);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes ripple {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.35);
  }
  40% {
    opacity: 1;
  }
  to {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.25);
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translate(-50%, 12px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}

.veil-enter-active {
  transition: opacity 320ms var(--ease);
}

.veil-leave-active {
  transition: opacity 520ms var(--ease);
  /* 抬幕布的过程里，幕布不该再挡住鼠标 */
  pointer-events: none;
}

.veil-enter-from,
.veil-leave-to {
  opacity: 0;
}
</style>
