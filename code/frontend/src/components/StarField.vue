<template>
  <canvas ref="canvas" class="starfield" aria-hidden="true"></canvas>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  count: { type: Number, default: 240 },
  tint: { type: String, default: 'cool' }
})

const canvas = ref(null)
let observer = null

function mulberry(seed) {
  let a = seed >>> 0
  return function () {
    a = (a + 0x6d2b79f5) >>> 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * 星空是**静止**的，故意不做闪烁。
 * 产品原则是「屏幕上同一时刻只有一个东西在动」，那个东西应该是声音。
 * 一片会闪的星空会把注意力从正在播的那段声音上拽走。
 * 没有动画循环 = 不占 CPU，滚动和播放都不会掉帧。
 */
function paint() {
  const cv = canvas.value
  if (!cv || !cv.clientWidth) return

  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const w = cv.clientWidth
  const h = cv.clientHeight
  cv.width = Math.round(w * dpr)
  cv.height = Math.round(h * dpr)

  const ctx = cv.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, w, h)

  const r = mulberry(20260918)
  const rgb = props.tint === 'warm' ? '235, 214, 170' : '207, 224, 255'

  for (let i = 0; i < props.count; i++) {
    const x = r() * w
    const y = r() * h
    const v = r()
    const size = v > 0.95 ? 1.7 : v > 0.72 ? 1.2 : 0.85
    const alpha = 0.10 + r() * 0.62

    ctx.beginPath()
    ctx.fillStyle = `rgba(${rgb}, ${alpha.toFixed(2)})`
    ctx.arc(x, y, size, 0, Math.PI * 2)
    ctx.fill()

    // 亮星给一圈极淡的晕，眼睛才会觉得「那是远处的一颗星」而不是一个点
    if (size > 1.5) {
      ctx.beginPath()
      ctx.fillStyle = `rgba(${rgb}, 0.055)`
      ctx.arc(x, y, size * 3.6, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

onMounted(() => {
  paint()
  observer = new ResizeObserver(paint)
  observer.observe(canvas.value)
})

onUnmounted(() => observer?.disconnect())

watch(() => [props.count, props.tint], paint)
</script>

<style scoped>
.starfield {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
