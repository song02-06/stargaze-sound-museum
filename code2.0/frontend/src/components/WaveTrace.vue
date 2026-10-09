<template>
  <svg
    class="trace"
    :class="{ 'is-warm': warm }"
    :viewBox="`0 0 ${w} ${h}`"
    role="img"
    :aria-label="label || '声音的波形'"
  >
    <path class="dim" :d="dimPath" />
    <template v-if="showLit">
      <path class="lit" :d="litPath" />
      <circle class="head" :cx="head.x" :cy="head.y" :r="mini ? 2 : 2.6" />
    </template>
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import { downsample, traceToPoints, pointsToPath } from '../lib/wave.js'

const props = defineProps({
  trace: { type: Array, default: () => [] },
  progress: { type: Number, default: 0 },
  warm: { type: Boolean, default: false },
  mini: { type: Boolean, default: false },
  label: { type: String, default: '' }
})

const w = computed(() => (props.mini ? 200 : 720))
const h = computed(() => (props.mini ? 30 : 132))

const data = computed(() =>
  props.mini ? downsample(props.trace, 96) : props.trace
)

const points = computed(() =>
  traceToPoints(data.value, w.value, h.value, props.mini ? 0.84 : 0.9)
)

const dimPath = computed(() => pointsToPath(points.value))

const cutIndex = computed(() => {
  const n = points.value.length - 1
  if (n < 1) return 0
  return Math.round(Math.max(0, Math.min(1, props.progress)) * n)
})

const litPath = computed(() => pointsToPath(points.value, cutIndex.value))

const head = computed(() => points.value[cutIndex.value] || { x: 0, y: h.value / 2 })

/** 进度为 0 时不画高亮段：声音还没开始，凭什么说它播过了 */
const showLit = computed(() => props.progress > 0.002 && points.value.length > 1)
</script>

<style scoped>
.trace {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.dim {
  fill: none;
  stroke: rgba(223, 231, 245, 0.20);
  stroke-width: 1.3;
}

.lit {
  fill: none;
  stroke: #e6edfa;
  stroke-width: 1.5;
}

.head {
  fill: #ffffff;
}

.is-warm .dim {
  stroke: rgba(214, 190, 144, 0.26);
}

.is-warm .lit {
  stroke: #d8bb84;
}

.is-warm .head {
  fill: #e8d5a8;
}
</style>
