<template>
  <div class="recorder glass">
    <header class="head">
      <h2 class="display">留一段声音</h2>
      <p class="lede">{{ statusText }}</p>
    </header>

    <!-- 录音时的实时电平：让用户确认「真的在录」 -->
    <div class="meter" :class="{ active: status === 'recording' }">
      <span v-for="i in 28" :key="i" class="tick" :style="tickStyle(i)"></span>
    </div>

    <div class="timer">
      <span class="now">{{ formattedDuration }}</span>
      <span class="limit">/ 01:00</span>
    </div>

    <input
      v-model="note"
      class="note"
      type="text"
      maxlength="60"
      placeholder="再写一句想说的话（可选）"
      :disabled="status === 'processing'"
    />

    <div class="actions">
      <button v-if="status === 'idle' || status === 'error'" class="btn primary lg" @click="start">
        开始录音
      </button>
      <button v-else-if="status === 'recording'" class="btn primary lg" @click="stop">
        停止并上传
      </button>
      <button v-else class="btn primary lg" disabled>处理中…</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <p class="tip">
      麦克风需要 HTTPS 或 localhost；录音会在浏览器里转成 16k 单声道 WAV 再上传，
      转写与 AI 润色都在这一步完成。
    </p>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { blobToWav16k } from '../utils/wav'

const emit = defineEmits(['recorded'])

const props = defineProps({
  maxMs: { type: Number, default: 60000 }
})

const status = ref('idle') // idle | recording | processing | error
const error = ref('')
const note = ref('')
const durationMs = ref(0)
const level = ref(0)

let mediaRecorder = null
let stream = null
let chunks = []
let startedAt = 0
let tickTimer = null
let audioContext = null
let analyser = null
let levelFrame = null

const statusText = computed(() => {
  switch (status.value) {
    case 'recording':
      return '正在聆听…说完点「停止并上传」'
    case 'processing':
      return '正在转码、转写与润色，请稍等'
    case 'error':
      return '出错了，可以重试'
    default:
      return '点击下面的按钮开始录音，最长 60 秒。'
  }
})

const formattedDuration = computed(() => {
  const total = Math.floor(durationMs.value / 1000)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
})

function tickStyle(i) {
  if (status.value !== 'recording') {
    return { opacity: 0.12, transform: 'scaleY(0.25)' }
  }
  // 中间的条更高，整体按电平缩放，做出简单的声浪形状
  const center = 1 - Math.abs(i - 14.5) / 14.5
  const h = 0.28 + center * 0.72 * level.value
  return { opacity: 0.35 + h * 0.65, transform: `scaleY(${Math.max(0.16, h).toFixed(2)})` }
}

async function start() {
  error.value = ''
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 }
    })
  } catch (e) {
    status.value = 'error'
    error.value = `无法访问麦克风：${e.message}`
    return
  }

  const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
    ? 'audio/webm;codecs=opus'
    : (MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '')
  mediaRecorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)

  chunks = []
  mediaRecorder.ondataavailable = (e) => {
    if (e.data.size > 0) chunks.push(e.data)
  }
  mediaRecorder.onstop = handleStop
  mediaRecorder.start(250)

  startedAt = Date.now()
  durationMs.value = 0
  status.value = 'recording'

  tickTimer = setInterval(() => {
    durationMs.value = Date.now() - startedAt
    if (durationMs.value >= props.maxMs) stop()
  }, 100)

  startLevelMeter()
}

function startLevelMeter() {
  audioContext = new (window.AudioContext || window.webkitAudioContext)()
  const source = audioContext.createMediaStreamSource(stream)
  analyser = audioContext.createAnalyser()
  analyser.fftSize = 512
  source.connect(analyser)

  const data = new Uint8Array(analyser.frequencyBinCount)
  const loop = () => {
    analyser.getByteFrequencyData(data)
    let sum = 0
    for (let i = 0; i < data.length; i++) sum += data[i]
    level.value = Math.min(1, sum / data.length / 90)
    levelFrame = requestAnimationFrame(loop)
  }
  loop()
}

function stopLevelMeter() {
  cancelAnimationFrame(levelFrame)
  audioContext?.close()
  audioContext = null
  analyser = null
  level.value = 0
}

function stop() {
  if (status.value !== 'recording') return
  durationMs.value = Date.now() - startedAt
  clearInterval(tickTimer)
  stopLevelMeter()
  status.value = 'processing'
  mediaRecorder.stop()
  stream?.getTracks().forEach((track) => track.stop())
}

async function handleStop() {
  try {
    const raw = new Blob(chunks, { type: mediaRecorder.mimeType || 'audio/webm' })
    if (durationMs.value < 3000) {
      throw new Error('录音太短了，至少说 3 秒')
    }
    const wav = await blobToWav16k(raw)
    emit('recorded', { blob: wav, durationMs: durationMs.value, note: note.value })
    status.value = 'idle'
    note.value = ''
  } catch (e) {
    status.value = 'error'
    error.value = `转码失败：${e.message}`
  }
}

onBeforeUnmount(() => {
  clearInterval(tickTimer)
  cancelAnimationFrame(levelFrame)
  audioContext?.close()
  stream?.getTracks().forEach((track) => track.stop())
})
</script>

<style scoped>
.recorder {
  width: min(100%, 560px);
  padding: clamp(26px, 3.4vw, 42px);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.head {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.head .display {
  font-size: clamp(22px, 2.8vw, 30px);
}

.meter {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 56px;
}

.tick {
  width: 3px;
  height: 100%;
  border-radius: var(--r-pill);
  background: linear-gradient(180deg, var(--accent), rgba(255, 207, 153, 0.9));
  transform-origin: center;
  transition: transform 0.09s linear, opacity 0.09s linear;
}

.meter:not(.active) .tick {
  background: var(--ink-3);
}

.timer {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}

.timer .now {
  font-size: 26px;
  font-family: var(--font-display);
  letter-spacing: 0.04em;
}

.timer .limit {
  font-size: 13px;
  color: var(--ink-3);
}

.note {
  width: 100%;
  padding: 13px 16px;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: rgba(6, 10, 22, 0.6);
  color: var(--ink);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s ease;
}

.note::placeholder {
  color: var(--ink-3);
}

.note:focus {
  border-color: var(--line-strong);
}

.actions {
  display: flex;
  justify-content: center;
}

.actions .btn {
  width: 100%;
  max-width: 260px;
}

.error {
  margin: 0;
  text-align: center;
  color: var(--danger);
  font-size: 13px;
}

.tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-3);
  text-align: center;
}
</style>
