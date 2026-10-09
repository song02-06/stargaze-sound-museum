<template>
  <div class="wrap">
    <p class="who">
      <span>以</span>
      <button v-if="!editing" class="nick" type="button" @click="startEdit">
        {{ nickname }}
      </button>
      <input
        v-else
        ref="nickInput"
        v-model="draftName"
        class="nick-input"
        maxlength="12"
        aria-label="昵称"
        @blur="commitName"
        @keydown.enter.prevent="commitName"
      />
      <span>的身份回音</span>
    </p>

    <div class="bar">
      <!-- 录音中 -->
      <template v-if="recording">
        <span class="rec"><em />录音中</span>
        <span class="time tnum">{{ clock(elapsed) }}</span>
        <span class="limit">至少 {{ MIN_SECONDS }} 秒，上限 {{ MAX_SECONDS }} 秒</span>
        <div class="actions">
          <button class="btn quiet" type="button" @click="cancelRecording">撤回</button>
          <button class="btn primary" type="button" @click="finishRecording">停止</button>
        </div>
      </template>

      <!-- 录完待发 -->
      <template v-else-if="take">
        <button class="play small" type="button" :aria-label="previewPlaying ? '暂停试听' : '试听'"
                @click="togglePreview">
          <AppIcon :name="previewPlaying ? 'pause' : 'play'" :size="13" />
        </button>
        <WaveTrace class="mini" mini :trace="take.trace" :progress="previewProgress" />
        <span class="time tnum">{{ clock(take.seconds) }}</span>
        <span v-if="tooShort" class="warn">太短了，至少 {{ MIN_SECONDS }} 秒</span>
        <div class="actions">
          <button class="btn quiet" type="button" @click="discardTake">重录</button>
          <button class="btn primary" type="button" :disabled="tooShort || busy" @click="sendVoice">
            发送
          </button>
        </div>
      </template>

      <!-- 常态：文字优先，语音并列 -->
      <template v-else>
        <textarea
          ref="inputEl"
          v-model="text"
          class="input"
          rows="1"
          placeholder="写下你想说的…"
          @input="grow"
          @keydown.enter.exact.prevent="sendText"
        />
        <div class="actions">
          <button
            class="mic"
            type="button"
            :disabled="!recordingSupported"
            :title="recordingSupported ? '' : '这个浏览器不支持录音'"
            @click="startRecording"
          >
            <em />说一句
          </button>
          <button
          class="send"
          type="button"
          aria-label="发送回音"
          :disabled="!text.trim() || busy"
          @click="sendText"
        >
            <AppIcon name="send" :size="15" />
          </button>
        </div>
      </template>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onUnmounted } from 'vue'
import WaveTrace from './WaveTrace.vue'
import AppIcon from './AppIcon.vue'
import { clock } from '../lib/format.js'
import { createRecorder, canRecord, peaksFromWav } from '../lib/audio.js'
import { nickname, setNickname } from '../store/museum.js'

const emit = defineEmits(['submit'])

defineProps({
  busy: { type: Boolean, default: false }
})

const text = ref('')
const inputEl = ref(null)
const editing = ref(false)
const draftName = ref('')
const nickInput = ref(null)
const error = ref('')

const recording = ref(false)
const elapsed = ref(0)
const take = ref(null)
const previewPlaying = ref(false)
const previewProgress = ref(0)

const recordingSupported = canRecord()
// 这两个数字和后端 application.yml 里的一致。前端只是提前告知，
// 真正的裁决在服务端 —— 改请求绕不过去。
const MIN_SECONDS = 10
const MAX_SECONDS = 30

const tooShort = computed(() => !!take.value && take.value.seconds < MIN_SECONDS)
let recorder = null
let ticker = 0
let previewAudio = null

function grow() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 132)}px`
}

function resetText() {
  text.value = ''
  nextTick(grow)
}

function startEdit() {
  draftName.value = nickname.value
  editing.value = true
  nextTick(() => nickInput.value?.select())
}

async function commitName() {
  editing.value = false
  try {
    await setNickname(draftName.value)
  } catch {
    error.value = '昵称没改成，先这样吧。'
  }
}

function sendText() {
  const body = text.value.trim()
  if (!body) return
  emit('submit', { kind: 'text', body })
  resetText()
}

async function startRecording() {
  error.value = ''
  try {
    recorder = createRecorder()
    await recorder.start()
    recording.value = true
    elapsed.value = 0
    ticker = window.setInterval(() => {
      elapsed.value += 0.1
      // 到上限自动停 —— 产品定义的 10–30 秒是硬约束，不是建议
      if (elapsed.value >= MAX_SECONDS) finishRecording()
    }, 100)
  } catch {
    error.value = '没能打开麦克风。检查一下浏览器权限，或者先写文字。'
    recorder = null
  }
}

function stopTicker() {
  window.clearInterval(ticker)
  ticker = 0
}

function cancelRecording() {
  stopTicker()
  recorder?.cancel()
  recorder = null
  recording.value = false
  elapsed.value = 0
}

async function finishRecording() {
  if (!recorder) return
  stopTicker()
  try {
    const { wav, seconds } = await recorder.stop()
    recorder = null
    recording.value = false
    // 本地先抽一遍波形，好在发送前就能试听和看形状；
    // 发出去之后以服务端从 WAV 里读出来的为准。
    const trace = await peaksFromWav(wav, 160)
    take.value = {
      url: URL.createObjectURL(wav),
      wav,
      seconds: Math.max(1, Math.round(seconds)),
      trace
    }
  } catch {
    recorder = null
    recording.value = false
    error.value = '这段录音没能保存下来，再试一次。'
  }
}

function discardTake() {
  if (take.value?.url) URL.revokeObjectURL(take.value.url)
  take.value = null
  previewPlaying.value = false
}

function togglePreview() {
  if (!take.value) return
  if (previewPlaying.value) {
    previewAudio?.pause()
    previewPlaying.value = false
    return
  }
  previewAudio?.pause()
  previewAudio = new Audio(take.value.url)
  previewAudio.addEventListener('timeupdate', () => {
    previewProgress.value = previewAudio.currentTime / (previewAudio.duration || 1)
  })
  previewAudio.addEventListener('ended', () => {
    previewPlaying.value = false
    previewProgress.value = 0
  })
  previewAudio.play().then(() => (previewPlaying.value = true)).catch(() => {
    error.value = '试听失败，但可以直接发送。'
  })
}

function sendVoice() {
  if (!take.value) return
  emit('submit', {
    kind: 'voice',
    wav: take.value.wav,
    seconds: take.value.seconds,
    trace: take.value.trace
  })
  take.value = null
  previewPlaying.value = false
  previewProgress.value = 0
}

onUnmounted(() => {
  stopTicker()
  recorder?.cancel()
  previewAudio?.pause()
})
</script>

<style scoped>
.wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.who {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-4);
}

.nick {
  color: var(--brass);
  border-bottom: 1px dashed rgba(201, 169, 107, 0.45);
  padding-bottom: 1px;
}

.nick:hover {
  border-bottom-style: solid;
}

.nick-input {
  width: 8ch;
  color: var(--brass);
  border-bottom: 1px solid var(--brass-2);
  text-align: center;
}

.bar {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 78px;
  padding: 14px 14px 14px 30px;
  border: 1px solid var(--line-2);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.018);
  transition: border-color var(--dur-fast) var(--ease);
}

.bar:focus-within {
  border-color: rgba(201, 169, 107, 0.42);
}

.input {
  flex: 1;
  min-width: 0;
  max-height: 132px;
  padding: 10px 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink);
  resize: none;
  overflow-y: auto;
}

.input::placeholder {
  color: var(--ink-4);
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.mic {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 24px;
  border: 1px solid var(--brass-2);
  border-radius: 999px;
  background: rgba(201, 169, 107, 0.05);
  font-size: 12px;
  letter-spacing: 0.22em;
  text-indent: 0.22em;
  color: var(--brass);
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.mic:hover:not(:disabled) {
  border-color: rgba(201, 169, 107, 0.55);
  background: rgba(201, 169, 107, 0.12);
}

.mic em {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brass);
}

.send {
  width: 48px;
  height: 48px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  font-size: 15px;
  color: var(--ink-3);
  transition: border-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.send:hover:not(:disabled) {
  border-color: rgba(255, 255, 255, 0.28);
  color: var(--ink);
}

.rec {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--ink);
}

.rec em {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--stamp);
  animation: pulse 1.4s var(--ease) infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.32;
  }
}

.play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-2);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.015);
  transition: border-color var(--dur-fast) var(--ease);
}

.play:hover {
  border-color: rgba(255, 255, 255, 0.28);
}

.play.small {
  width: 34px;
  height: 34px;
  flex: none;
}

.mini {
  flex: 1;
  min-width: 80px;
}

.time {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--ink-4);
  white-space: nowrap;
}

.limit {
  font-size: 11px;
  color: var(--ink-4);
  letter-spacing: 0.1em;
}

.warn {
  font-size: 12px;
  color: #d98b7c;
}

.error {
  font-size: 12px;
  color: #d98b7c;
}

@media (max-width: 760px) {
  .bar {
    flex-wrap: wrap;
    padding: 14px 14px 14px 18px;
    gap: 12px;
  }

  .input {
    flex-basis: 100%;
  }

  .limit {
    display: none;
  }
}
</style>
