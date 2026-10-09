<template>
  <div class="player">
    <button class="play" :aria-label="playing ? '暂停' : '播放'" @click="toggle">
      <svg v-if="!playing" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path d="M8 5.5v13l11-6.5z" fill="currentColor" />
      </svg>
      <svg v-else viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path d="M7.5 5h3.2v14H7.5zM13.3 5h3.2v14h-3.2z" fill="currentColor" />
      </svg>
    </button>

    <div ref="waveWrap" class="wave" @click="seekFromEvent">
      <canvas ref="canvas"></canvas>
      <span v-if="!peaks" class="wave-hint">{{ peaksFailed ? '波形不可用' : '波形加载中…' }}</span>
    </div>

    <span class="time">{{ formattedCurrent }} <i>/</i> {{ formattedDuration }}</span>

    <audio
      ref="audioEl"
      :src="src"
      preload="metadata"
      @loadedmetadata="onMeta"
      @timeupdate="onTime"
      @play="onPlay"
      @pause="onPause"
      @ended="onEnded"
    ></audio>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { loadPeaks } from '../utils/peaks'

const props = defineProps({
  src: { type: String, default: '' },
  // 是否把实时音量抛给父组件（用于驱动星空）
  emitLevel: { type: Boolean, default: true }
})

const emit = defineEmits(['level', 'ended'])

const audioEl = ref(null)
const canvas = ref(null)
const waveWrap = ref(null)

const playing = ref(false)
const current = ref(0)
const duration = ref(0)
const peaks = ref(null)
const peaksFailed = ref(false)

let audioCtx = null
let analyser = null
let freqData = null
let rafId = null
let resizeObs = null

const formattedCurrent = computed(() => fmt(current.value))
const formattedDuration = computed(() => fmt(duration.value || 0))

function fmt(sec) {
  const s = Math.max(0, Math.floor(sec || 0))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function toggle() {
  const el = audioEl.value
  if (!el) return
  if (el.paused) el.play().catch(() => {})
  else el.pause()
}

function onMeta() {
  duration.value = audioEl.value?.duration || 0
  draw()
}

function onTime() {
  current.value = audioEl.value?.currentTime || 0
  if (!playing.value) draw()
}

function onPlay() {
  playing.value = true
  ensureAnalyser()
  audioCtx?.resume()
  loop()
}

function onPause() {
  playing.value = false
  stopLoop()
  emit('level', 0)
  draw()
}

function onEnded() {
  playing.value = false
  stopLoop()
  emit('level', 0)
  emit('ended')
  draw()
}

function seekFromEvent(e) {
  const el = audioEl.value
  const rect = waveWrap.value?.getBoundingClientRect()
  if (!el || !rect || !duration.value) return
  const ratio = Math.min(Math.max((e.clientX - rect.left) / rect.width, 0), 1)
  el.currentTime = ratio * duration.value
  current.value = el.currentTime
  draw()
}

/** 播放时才建立 AudioContext：避免页面一加载就占用音频通道 */
function ensureAnalyser() {
  if (audioCtx || !audioEl.value) return
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    audioCtx = new Ctx()
    const source = audioCtx.createMediaElementSource(audioEl.value)
    analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    analyser.smoothingTimeConstant = 0.82
    source.connect(analyser)
    analyser.connect(audioCtx.destination)
    freqData = new Uint8Array(analyser.frequencyBinCount)
  } catch {
    audioCtx = null
  }
}

function loop() {
  rafId = requestAnimationFrame(loop)
  if (analyser && freqData && props.emitLevel) {
    analyser.getByteFrequencyData(freqData)
    let sum = 0
    for (let i = 0; i < freqData.length; i++) sum += freqData[i]
    emit('level', Math.min(1, sum / freqData.length / 80))
  }
  draw()
}

function stopLoop() {
  cancelAnimationFrame(rafId)
  rafId = null
}

/** 画波形：已播放部分用星光色，未播放部分压暗，再叠一根播放头 */
function draw() {
  const cvs = canvas.value
  if (!cvs) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const w = cvs.clientWidth
  const h = cvs.clientHeight
  if (!w || !h) return

  if (cvs.width !== Math.round(w * dpr) || cvs.height !== Math.round(h * dpr)) {
    cvs.width = Math.round(w * dpr)
    cvs.height = Math.round(h * dpr)
  }

  const ctx = cvs.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, w, h)

  const data = peaks.value
  if (!data) return

  const n = data.length
  const slot = w / n
  const barW = Math.max(1, slot * 0.56)
  const mid = h / 2
  const ratio = duration.value ? Math.min(current.value / duration.value, 1) : 0

  for (let i = 0; i < n; i++) {
    const amp = Math.max(0.06, data[i])
    const barH = amp * (h * 0.86)
    const x = i * slot + (slot - barW) / 2
    const played = i / n <= ratio
    ctx.fillStyle = played ? 'rgba(159, 216, 255, 0.92)' : 'rgba(238, 242, 255, 0.18)'
    ctx.beginPath()
    const r = barW / 2
    const y = mid - barH / 2
    ctx.roundRect(x, y, barW, barH, r)
    ctx.fill()
  }

  if (ratio > 0) {
    const px = ratio * w
    ctx.fillStyle = 'rgba(255, 255, 255, 0.85)'
    ctx.fillRect(px - 0.5, 0, 1, h)
  }
}

function scheduleDraw() {
  draw()
}

onMounted(async () => {
  resizeObs = new ResizeObserver(scheduleDraw)
  if (waveWrap.value) resizeObs.observe(waveWrap.value)

  if (props.src) {
    try {
      peaks.value = await loadPeaks(props.src)
    } catch {
      peaksFailed.value = true
    }
    draw()
  }
})

watch(
  () => props.src,
  async (url) => {
    peaks.value = null
    peaksFailed.value = false
    if (!url) return
    try {
      peaks.value = await loadPeaks(url)
    } catch {
      peaksFailed.value = true
    }
    draw()
  }
)

onBeforeUnmount(() => {
  stopLoop()
  resizeObs?.disconnect()
  audioCtx?.close()
})
</script>

<style scoped>
.player {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: var(--r-md);
  background: rgba(6, 10, 22, 0.6);
  border: 1px solid var(--line);
}

.play {
  flex: none;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #05101f;
  background: linear-gradient(130deg, var(--accent), var(--warm));
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.play:hover {
  transform: scale(1.06);
  box-shadow: 0 0 22px rgba(159, 216, 255, 0.4);
}

.wave {
  position: relative;
  flex: 1;
  height: 46px;
  cursor: pointer;
  min-width: 80px;
}

.wave canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.wave-hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 11px;
  color: var(--ink-3);
  pointer-events: none;
}

.time {
  flex: none;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--ink-2);
}

.time i {
  font-style: normal;
  color: var(--ink-3);
  margin: 0 2px;
}
</style>
