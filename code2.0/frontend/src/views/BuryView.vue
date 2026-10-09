<template>
  <div class="bury">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
        <RouterLink to="/mine">我的声音</RouterLink>
      </template>
    </AppTopBar>

    <!-- 单件东西的页面在这个馆里都是居中的 ——
         左边是器物，右边是它的著录牌，窄屏自动叠成一列 -->
    <div class="col">
      <header class="head">
        <p class="eyebrow">埋下一段</p>
        <h1 class="serif">留一段声音在星空里</h1>
        <p class="lede">
          四样东西齐了才算一件展品：声音本身、它的名字、你亲手写的故事，
          还有它是什么时候、在哪儿录的。通过审核之后，它会沉进星空，
          等人捞起来 —— 也可能很久没人捞到，那也没关系。
        </p>
      </header>

      <div class="board" :class="{ sinking }">
        <!-- ---------- 左：器物 ---------- -->
        <div class="artifact">
          <!-- 井口：这一段声音从这里下去。和首页那个井口是同一套几何 -->
          <div class="well" aria-hidden="true"><span /></div>

          <div class="face">
            <!-- 交出去之后：器物空了，只剩一句它在哪 -->
            <p v-if="result" class="fate serif">{{ outcome.fate }}</p>

            <template v-else-if="recording">
              <p class="live"><em />录音中 <span class="tnum">{{ clock(elapsed) }}</span></p>
              <p class="hint">至少 {{ MIN_SECONDS }} 秒，上限 {{ MAX_SECONDS }} 秒 —— 到点会自动停。</p>
            </template>

            <template v-else-if="take">
              <WaveTrace
                class="trace"
                :trace="take.trace"
                :progress="previewProgress"
                label="你录下的这段声音的声波"
              />
              <p class="line">
                <button
                  class="play"
                  type="button"
                  :aria-label="previewPlaying ? '暂停试听' : '试听'"
                  @click="togglePreview"
                >
                  <AppIcon :name="previewPlaying ? 'pause' : 'play'" :size="13" />
                </button>
                <span class="tnum">{{ clock(take.seconds) }}</span>
                <span class="from">{{ take.from }}</span>
              </p>
              <p v-if="tooShort" class="warn">太短了，至少 {{ MIN_SECONDS }} 秒。再录一遍。</p>
            </template>

            <template v-else>
              <p class="invite serif">这一段还没有声音</p>
              <p class="hint">
                {{ MIN_SECONDS }}–{{ MAX_SECONDS }} 秒。
                <template v-if="canRecord()">现场录一段，或者选一个已经录好的文件。</template>
                <template v-else>这个浏览器不能录音，选一个已经录好的文件吧。</template>
                选文件会在你的浏览器里转成 WAV 再上传，wav / mp3 / m4a / ogg 都行。
              </p>
            </template>
          </div>

          <div v-if="!result" class="acts">
            <template v-if="recording">
              <button class="btn" type="button" @click="cancelRecording">撤回</button>
              <button class="btn primary" type="button" @click="finishRecording">停止</button>
            </template>

            <template v-else-if="take">
              <button class="btn quiet" type="button" @click="clearTake">重来</button>
            </template>

            <template v-else>
              <button
                v-if="canRecord()"
                class="btn"
                type="button"
                :disabled="decoding"
                @click="startRecording"
              >
                按下开始录音
              </button>
              <span class="file">
                <input id="bury-file" class="visually-hidden" type="file" accept="audio/*" @change="pickFile" />
                <label class="btn quiet file-label" for="bury-file">
                  {{ decoding ? '正在读取…' : '选一个音频文件' }}
                </label>
              </span>
            </template>
          </div>
        </div>

        <!-- ---------- 右：著录牌 ---------- -->
        <!-- 点这里任何一处就取消「马上带你走」 -->
        <section v-if="result" class="plate done" @click="holdHere">
          <p class="eyebrow step s1" :class="{ 'is-brass': result.status === 'PASS' }">
            {{ outcome.eyebrow }}
          </p>
          <h2 class="serif step s2">《{{ result.title }}》</h2>
          <p class="meta tnum step s3">
            Nº {{ result.no }} · {{ dotDate(result.date) }} · {{ result.place }}
          </p>
          <p class="said step s4">{{ outcome.said }}</p>
          <p v-if="result.auditNote" class="why step s4">{{ result.auditNote }}</p>

          <div class="acts step s5">
            <RouterLink v-if="result.status === 'PASS'" class="btn primary" :to="`/exhibit/${result.no}`">
              去看它
            </RouterLink>
            <RouterLink v-else class="btn primary" to="/mine">去看它现在在哪</RouterLink>
            <button v-if="result.status !== 'PASS'" class="btn" type="button" @click="result = null">
              回去改一改
            </button>
            <button class="btn quiet" type="button" @click="reset">再埋一段</button>
          </div>

          <p v-if="leaving" class="hint">{{ leavingNote }}</p>
        </section>

        <form v-else class="plate form" @submit.prevent="submit">
          <div class="row">
            <label class="key" for="bury-title">名字</label>
            <div class="val">
              <input
                id="bury-title"
                v-model="title"
                class="text"
                type="text"
                maxlength="80"
                placeholder="比如：外婆家屋檐下的雨"
              />
            </div>
          </div>

          <div class="row">
            <label class="key" for="bury-full">故事</label>
            <div class="val">
              <textarea
                id="bury-full"
                v-model="full"
                class="area"
                rows="5"
                maxlength="400"
                placeholder="那是什么样的声音，当时你在哪、在做什么。写下你记得的部分就够了。"
              />
              <p class="hint">
                <span class="tnum">{{ full.trim().length }}</span> / {{ MAX_FULL_CHARS }}，至少
                {{ MIN_FULL_CHARS }} 个字。
                展签由系统从你写的原文里截取 —— 只做减法，不会替你编。
              </p>
            </div>
          </div>

          <div class="row">
            <span class="key">语境</span>
            <div class="val two">
              <span class="pair">
                <label class="sub" for="bury-place">地点</label>
                <input
                  id="bury-place"
                  v-model="place"
                  class="text"
                  type="text"
                  maxlength="60"
                  placeholder="广州 · 老城区"
                />
                <span class="hint">写到街区一级就行。别写门牌、单位、别人的名字。</span>
              </span>
              <span class="pair">
                <label class="sub" for="bury-date">录制日期</label>
                <input id="bury-date" v-model="recordedOn" class="text tnum" type="date" :max="today" />
                <span class="hint">这段声音是哪天录的，不能是未来。</span>
              </span>
            </div>
          </div>

          <div class="row">
            <span class="key">出处</span>
            <div class="val">
              <label class="agree">
                <input v-model="agreed" type="checkbox" />
                <span>这段声音是我自己录的或自己创作的，里面没有别人的音乐作品。</span>
              </label>
              <p class="hint">演唱类的会转人工复审，慢一点，但会有人认真听。</p>
            </div>
          </div>

          <div class="submit">
            <button class="btn primary" type="submit" :disabled="!canSubmit || busy">
              {{ busy ? '正在沉下去…' : '沉入星空' }}
            </button>
            <p v-if="!readyHint" class="hint">交上去之后先过机器审核，机器拿不准的会转人工看一眼。</p>
            <p v-else class="hint">{{ readyHint }}</p>
          </div>

          <p v-if="error" class="error" role="alert">{{ error }}</p>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import AppIcon from '../components/AppIcon.vue'
import WaveTrace from '../components/WaveTrace.vue'
import { createRecorder, canRecord, blobToWav16k, peaksFromWav } from '../lib/audio.js'
import { clock, dotDate } from '../lib/format.js'
import * as api from '../api/index.js'

const router = useRouter()

// 这两个数字和后端 application.yml 里的一致。前端只是提前告知，
// 真正的裁决在服务端 —— 改请求绕不过去。
const MIN_SECONDS = 10
const MAX_SECONDS = 60
const MIN_FULL_CHARS = 15
const MAX_FULL_CHARS = 400

const title = ref('')
const full = ref('')
const place = ref('')
const recordedOn = ref(new Date().toISOString().slice(0, 10))
const agreed = ref(false)
const today = new Date().toISOString().slice(0, 10)

const take = ref(null)
const recording = ref(false)
const elapsed = ref(0)
const decoding = ref(false)
const busy = ref(false)
const error = ref('')
const result = ref(null)
/** 交出去的那一下：声波往下沉，沉到底再露结果 */
const sinking = ref(false)
const previewPlaying = ref(false)
const previewProgress = ref(0)

let recorder = null
let ticker = 0
let previewAudio = null
let settle = null

const tooShort = computed(() => !!take.value && take.value.seconds < MIN_SECONDS)

const canSubmit = computed(
  () =>
    !!take.value &&
    !tooShort.value &&
    title.value.trim().length > 0 &&
    full.value.trim().length >= MIN_FULL_CHARS &&
    full.value.trim().length <= MAX_FULL_CHARS &&
    place.value.trim().length > 0 &&
    !!recordedOn.value &&
    recordedOn.value <= today &&
    agreed.value
)

/** 还差什么，就说那一样。不说「请填写完整表单」这种话。 */
const readyHint = computed(() => {
  if (!take.value) return '还差一段声音。'
  if (tooShort.value) return `还差几秒 —— 至少 ${MIN_SECONDS} 秒。`
  if (!title.value.trim()) return '还差一个名字。'
  if (full.value.trim().length < MIN_FULL_CHARS) {
    return `故事还差 ${MIN_FULL_CHARS - full.value.trim().length} 个字。`
  }
  if (!place.value.trim()) return '还差一个地点。'
  if (!recordedOn.value || recordedOn.value > today) return '录制日期要填成一个过去的日子。'
  if (!agreed.value) return '还差那个勾。'
  return ''
})

const OUTCOMES = {
  PASS: {
    eyebrow: '已经沉入星空',
    fate: '它下去了',
    said: '机器看过，没问题，它进馆了。现在它可以被人捞到了。'
  },
  REVIEW: {
    eyebrow: '转人工复审了',
    fate: '它在等一个人',
    said: '机器拿不准 —— 这不是拒绝，只是需要一个人认真听一遍。通过之后它会自己进馆。'
  },
  REJECT: {
    eyebrow: '这一段没能通过',
    fate: '它还在你手上',
    said: '它没有进馆。你可以改一改上面那几样，再交一次。'
  },
  PENDING: {
    eyebrow: '正在审核',
    fate: '它还在路上',
    said: '收到了，还在过审核。'
  }
}

const outcome = computed(() => OUTCOMES[result.value?.status] || OUTCOMES.PENDING)

/**
 * 交出去之后不该把人留在原地 —— 但也不能把人硬拽走。
 *
 * 能去看的（进馆 / 转人工）等编号落定之后自己走过去；
 * 没通过的**不动**：那一条有动作要做（改一改再交），把人跳走才是尴尬。
 * 任何时候点一下这块牌就取消。
 */
const leaving = ref(false)
let leaveTimer = 0

watch(result, (value) => {
  window.clearTimeout(leaveTimer)
  leaving.value = false
  if (!value || (value.status !== 'PASS' && value.status !== 'REVIEW')) return

  leaving.value = true
  leaveTimer = window.setTimeout(() => {
    router.push(value.status === 'PASS' ? `/exhibit/${value.no}` : '/mine')
  }, 2200)
})

const leavingNote = computed(() =>
  result.value?.status === 'PASS'
    ? '马上带你去看它。不想走的话，点这块牌上任何一处就留在这里。'
    : '马上带你去看它现在在哪。不想走的话，点这块牌上任何一处就留在这里。'
)

function holdHere() {
  window.clearTimeout(leaveTimer)
  leaveTimer = 0
  leaving.value = false
}

/* ---------- 录音 ---------- */

async function startRecording() {
  error.value = ''
  try {
    recorder = createRecorder()
    await recorder.start()
    recording.value = true
    elapsed.value = 0
    ticker = window.setInterval(() => {
      elapsed.value += 0.1
      // 到上限自动停 —— 60 秒是硬约束，不是建议
      if (elapsed.value >= MAX_SECONDS) finishRecording()
    }, 100)
  } catch {
    error.value = '没能打开麦克风。检查一下浏览器的权限，或者改选一个音频文件。'
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
    await adopt(wav, Math.max(1, Math.round(seconds)), '刚刚录的')
  } catch {
    recorder = null
    recording.value = false
    error.value = '这段录音没能保存下来，再试一次。'
  }
}

/* ---------- 选文件 ---------- */

async function pickFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  error.value = ''
  decoding.value = true
  try {
    // 浏览器里统一转成 16k 单声道 WAV —— 后端只认 WAV，而用户手上可能是手机录音
    const wav = await blobToWav16k(file)
    await adopt(wav, wavSeconds(wav), file.name)
  } catch {
    error.value = '这个文件没能读成音频，换一个试试（wav / mp3 / m4a / ogg）。'
  } finally {
    decoding.value = false
  }
}

function wavSeconds(wav) {
  return Math.max(1, Math.round((wav.size - 44) / 2 / 16000))
}

async function adopt(wav, seconds, from) {
  clearTake()
  take.value = {
    wav,
    seconds,
    from,
    url: URL.createObjectURL(wav),
    trace: await peaksFromWav(wav, 240)
  }
}

function clearTake() {
  if (take.value?.url) URL.revokeObjectURL(take.value.url)
  if (previewAudio) {
    previewAudio.pause()
    previewAudio = null
  }
  previewPlaying.value = false
  previewProgress.value = 0
  take.value = null
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
    error.value = '试听失败了，不过可以直接交上去。'
  })
}

/* ---------- 落定的那一记声音 ---------- */

/**
 * 打捞是三记、向上、勾住；埋下是一记、向下、收束。
 * 它只落在声波沉到底的那一刻 —— 之后什么都不放。
 */
function preloadSettle() {
  if (reduceMotion()) return
  settle = new Audio()
  settle.preload = 'auto'
  settle.src = '/audio/bury/settle.wav'
  settle.volume = 0.38
}

function playSettle() {
  if (reduceMotion() || !settle) return
  settle.currentTime = 0
  // 自动播放被拦下就算了：声音是注解，不该挡住这件事本身
  settle.play().catch(() => {})
}

/* ---------- 提交 ---------- */

async function submit() {
  if (!canSubmit.value || busy.value) return
  busy.value = true
  error.value = ''
  sinking.value = true

  // 下沉这一下要看得见，但请求回来得快的时候不该硬等 —— 两者取长的那个
  const startedAt = Date.now()
  const sinkMs = reduceMotion() ? 0 : 700

  try {
    const detail = await api.uploadExhibit({
      file: take.value.wav,
      title: title.value.trim(),
      full: full.value.trim(),
      place: place.value.trim(),
      recordedOn: recordedOn.value
    })
    // 先把这一下沉完，再换画面 —— 否则结果会在动画中途顶掉它
    await sleep(Math.max(0, sinkMs - (Date.now() - startedAt)))
    playSettle()
    result.value = detail
    window.scrollTo({ top: 0 })
  } catch (e) {
    // 没交上去就把这一页还给用户，不能让他对着一个沉下去的空白页
    sinking.value = false
    // 服务端的原话就是最好的提示：时长、字数、审核原因都在里面
    error.value = e?.message || '没能交上去，再试一次。'
  } finally {
    busy.value = false
  }
}

function reset() {
  result.value = null
  error.value = ''
  title.value = ''
  full.value = ''
  place.value = ''
  recordedOn.value = today
  agreed.value = false
  clearTake()
}

onMounted(() => {
  // 别和首屏抢带宽，等页面落定再把这一小段拉下来
  window.setTimeout(preloadSettle, 900)
})

onUnmounted(() => {
  window.clearTimeout(leaveTimer)
  stopTicker()
  recorder?.cancel()
  previewAudio?.pause()
  settle?.pause()
  if (take.value?.url) URL.revokeObjectURL(take.value.url)
})

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const reduceMotion = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches
</script>

<style scoped>
.bury {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.col {
  width: min(calc(100% - var(--rail) * 2), 1120px);
  margin-inline: auto;
  padding-bottom: 76px;
}

.head {
  padding: 60px 0 48px;
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

/* ---------- 器物 + 著录牌：一件东西，和它的标签 ---------- */

.board {
  display: grid;
  grid-template-columns: minmax(0, 0.86fr) minmax(0, 1.14fr);
  gap: 32px 64px;
  align-items: center;
}

.artifact {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 430px;
  padding: 24px 0;
}

/* 井口：同心细环，和首页那个井口是同一套几何。
   一组环本身就有纵深，不用阴影、不用发光 */
.well {
  position: absolute;
  inset: 0;
  margin: auto;
  width: min(100%, 340px);
  aspect-ratio: 1;
  border: 1px solid rgba(198, 218, 255, 0.13);
  border-radius: 50%;
  pointer-events: none;
  animation: ring-in 760ms var(--ease) both;
}

.well::before,
.well::after,
.well span {
  content: '';
  position: absolute;
  border-radius: 50%;
}

.well::before {
  inset: 14%;
  border: 1px solid rgba(198, 218, 255, 0.095);
  animation: ring-in 760ms var(--ease) 120ms both;
}

.well::after {
  inset: 30%;
  border: 1px solid rgba(198, 218, 255, 0.07);
  animation: ring-in 760ms var(--ease) 240ms both;
}

.well span {
  inset: 44%;
  border: 1px solid rgba(198, 218, 255, 0.055);
  animation: ring-in 760ms var(--ease) 360ms both;
}

/* 仪器对准：环从外到内依次亮一次，只放一次，之后完全静止 */
@keyframes ring-in {
  from {
    opacity: 0;
    transform: scale(1.035);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.face {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  text-align: center;
  transition: opacity 700ms var(--ease);
}

.invite {
  margin-bottom: 14px;
  font-size: clamp(19px, 1.9vw, 23px);
  font-weight: 300;
  color: var(--ink-2);
}

.fate {
  font-size: clamp(19px, 1.9vw, 23px);
  font-weight: 300;
  color: var(--ink-2);
}

.face .hint {
  max-width: 34ch;
}

.trace {
  transition: transform 700ms var(--ease), opacity 700ms var(--ease);
}

.line {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
  font-size: 12px;
  color: var(--ink-4);
}

.from {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 22ch;
}

.play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line-2);
  border-radius: 50%;
  color: var(--ink-2);
  transition: border-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.play:hover {
  border-color: rgba(255, 255, 255, 0.28);
  color: var(--ink);
}

.live {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--ink);
}

.live em {
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

.acts {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 26px;
  flex-wrap: wrap;
}

.warn {
  margin-top: 14px;
  font-size: 12px;
  color: #d98b7c;
}

/* 交出去的那一下：声波往下沉、压平，整块牌同时让开。
   井口留着 —— 东西是从那里下去的 */
.board.sinking {
  pointer-events: none;
}

.board.sinking .face,
.board.sinking .acts {
  opacity: 0;
}

.board.sinking .trace {
  transform: translateY(30px) scaleY(0.06);
  opacity: 0;
}

/* ---------- 著录牌 ---------- */

.plate {
  border-top: 1px solid rgba(201, 169, 107, 0.42);
  border-bottom: 1px solid var(--line);
  padding: 26px 32px 32px;
  transition: opacity 700ms var(--ease);
}

.board.sinking .plate {
  opacity: 0;
}

.row {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 26px;
  padding: 26px 0;
  border-top: 1px solid var(--line);
}

.row:first-child {
  border-top: none;
  padding-top: 4px;
}

.key {
  padding-top: 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  color: var(--brass);
}

.val {
  min-width: 0;
}

.val.two {
  display: grid;
  grid-template-columns: 1fr 176px;
  gap: 28px;
}

.pair {
  display: block;
  min-width: 0;
}

.sub {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--ink-3);
}

/* 值直接排在牌面上：不聚焦时没有框，只有一条几乎看不见的基线 */
.text,
.area {
  display: block;
  width: 100%;
  padding: 8px 2px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.045);
  font-size: 15px;
  font-weight: 300;
  color: var(--ink);
  transition: border-color var(--dur-fast) var(--ease);
}

.text:hover,
.area:hover {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.text:focus,
.area:focus {
  border-bottom-color: var(--brass-2);
}

/* 牌面比页面底色亮一档（那层黄铜洗地），所以牌面上的次级文字用 --ink-3：
   --ink-4 在这个底色上只有 4.45:1，差一点过不了 */
.text::placeholder,
.area::placeholder {
  color: var(--ink-3);
}

.area {
  min-height: 128px;
  line-height: 2;
  resize: vertical;
}

.hint {
  margin-top: 10px;
  font-size: 11px;
  line-height: 1.9;
  color: var(--ink-3);
}

.error {
  margin-top: 20px;
  padding: 13px 16px;
  border: 1px solid rgba(217, 139, 124, 0.34);
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.9;
  color: #e0a093;
}

.agree {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 14px;
  font-weight: 300;
  line-height: 1.9;
  color: var(--ink-2);
  cursor: pointer;
}

.agree input {
  flex: none;
  width: 16px;
  height: 16px;
  margin-top: 6px;
  accent-color: var(--brass);
  cursor: pointer;
}

.submit {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  padding-top: 30px;
  border-top: 1px solid var(--line);
}

.submit .hint {
  margin-top: 0;
  flex: 1;
  min-width: 180px;
}

.file {
  display: inline-flex;
}

.file:focus-within .file-label {
  outline: 1px solid var(--brass);
  outline-offset: 3px;
  border-radius: 999px;
}

/* ---------- 交出去之后：牌上记着它现在在哪 ---------- */

.done {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.done h2 {
  font-size: clamp(21px, 2.2vw, 27px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
}

/* 落定：编号先站住，其余跟着出来。一次动效，不是一串入场 */
.step {
  opacity: 0;
  transform: translateY(10px);
  animation: settle 560ms var(--ease) both;
}

.s1 {
  animation-delay: 0ms;
}

.s2 {
  animation-delay: 90ms;
}

.s3 {
  animation-delay: 200ms;
}

.s4 {
  animation-delay: 320ms;
}

.s5 {
  animation-delay: 420ms;
}

@keyframes settle {
  to {
    opacity: 1;
    transform: none;
  }
}

.meta {
  font-size: 12px;
  color: var(--brass);
}

.said {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 300;
  line-height: 2;
  color: var(--ink-2);
}

.why {
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-3);
}

.done .acts {
  justify-content: flex-start;
  margin-top: 18px;
}

.is-brass {
  color: var(--brass);
}

@media (max-width: 1080px) {
  .board {
    gap: 32px 48px;
  }
}

@media (max-width: 880px) {
  .board {
    grid-template-columns: 1fr;
    gap: 44px;
  }

  .artifact {
    min-height: 360px;
  }

  .head {
    padding: 40px 0 32px;
  }

  .plate {
    padding: 20px 22px 26px;
  }

  .row {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 22px 0;
  }

  .key {
    padding-top: 0;
  }

  .val.two {
    grid-template-columns: 1fr;
    gap: 22px;
  }
}
</style>
