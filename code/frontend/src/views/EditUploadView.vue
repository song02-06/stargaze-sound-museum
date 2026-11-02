<template>
  <div class="edit">
    <AppTopBar>
      <template #left>
        <RouterLink to="/mine">
          <AppIcon name="chevron-left" :size="13" />回到我的声音
        </RouterLink>
      </template>
      <template #right>
        <span v-if="no">Nº {{ no }}</span>
      </template>
    </AppTopBar>

    <div class="col">
      <header class="head">
        <p class="eyebrow">改一改</p>
        <h1 class="serif">还是那一件，只是换了说法</h1>
        <p class="lede">
          标题、故事、地点、日期和分类都能改。改的是<b>同一件</b>投稿 ——
          编号不变，也不会多出一件。改完要重新过一遍审核，
          所以在它通过之前，别人看不到新内容。
        </p>
        <p class="lede note">
          声音本身在投稿之后就固定了：换一段声音等于换了一件东西，那应该重新埋一段。
        </p>
      </header>

      <div class="rail">
        <StateNote v-if="loading">正在把这一件提上来…</StateNote>
        <StateNote v-else-if="loadError" error :on-retry="load">{{ loadError }}</StateNote>
      </div>

      <div v-if="!loading && !loadError && source" class="board">
        <!-- 左：原来的声音还在 -->
        <div class="artifact">
          <div class="well" aria-hidden="true"><span /></div>
          <div class="face">
            <p class="invite serif">这一段声音没有变</p>
            <audio class="player" :src="source.audioUrl" controls preload="metadata" />
            <p class="hint tnum">{{ source.seconds }} 秒 · Nº {{ source.no }}</p>
            <p class="state" :class="`is-${String(source.status).toLowerCase()}`">
              {{ STATUS[source.status] || source.status }}
            </p>
            <p v-if="source.auditNote" class="why">{{ source.auditNote }}</p>
          </div>
        </div>

        <!-- 右：可改的那几样，和投稿时同一套牌面 -->
        <section v-if="result" class="plate done">
          <p class="eyebrow step s1" :class="{ 'is-brass': result.status === 'PASS' }">
            {{ OUTCOMES[result.status]?.eyebrow || '已经改好了' }}
          </p>
          <h2 class="serif step s2">《{{ result.title }}》</h2>
          <p class="meta tnum step s3">Nº {{ result.no }} · {{ dotDate(result.date) }} · {{ result.place }}</p>
          <p class="said step s4">{{ OUTCOMES[result.status]?.said }}</p>
          <p v-if="result.auditNote" class="why step s4">{{ result.auditNote }}</p>

          <div class="acts step s5">
            <RouterLink v-if="result.status === 'PASS'" class="btn primary" :to="`/exhibit/${result.no}`">
              去看它
            </RouterLink>
            <RouterLink v-else class="btn primary" to="/mine">去看它现在在哪</RouterLink>
            <button class="btn" type="button" @click="backToForm">接着改</button>
          </div>
        </section>

        <form v-else class="plate form" @submit.prevent="submit">
          <div class="row">
            <label class="key" for="edit-title">名字</label>
            <div class="val">
              <input id="edit-title" v-model="form.title" class="text" type="text" maxlength="80" />
            </div>
          </div>

          <div class="row">
            <label class="key" for="edit-full">故事</label>
            <div class="val">
              <textarea id="edit-full" v-model="form.full" class="area" rows="5" maxlength="400" />
              <p class="hint">
                <span class="tnum">{{ form.full.trim().length }}</span> / {{ MAX_FULL_CHARS }}，至少
                {{ MIN_FULL_CHARS }} 个字。
                展签会按新故事重新生成 —— 只做减法，不会替你编。
              </p>
            </div>
          </div>

          <div class="row">
            <span class="key">语境</span>
            <div class="val two">
              <span class="pair">
                <label class="sub" for="edit-place">地点</label>
                <input id="edit-place" v-model="form.place" class="text" type="text" maxlength="60" />
              </span>
              <span class="pair">
                <label class="sub" for="edit-date">录制日期</label>
                <input id="edit-date" v-model="form.recordedOn" class="text tnum" type="date" :max="today" />
              </span>
            </div>
          </div>

          <div class="row">
            <span class="key">分类</span>
            <div class="val">
              <div class="types">
                <label v-for="t in SOUND_TYPES" :key="t.value" class="type">
                  <input v-model="form.soundType" type="radio" name="edit-type" :value="t.value" />
                  <span class="type-name">{{ t.label }}</span>
                  <span class="type-note">{{ t.note }}</span>
                </label>
              </div>
            </div>
          </div>

          <div class="row">
            <span class="key">出处</span>
            <div class="val">
              <label class="agree">
                <input v-model="form.originalConfirmed" type="checkbox" />
                <span>这段声音是我自己录的或自己创作的，里面没有别人的音乐作品。</span>
              </label>
            </div>
          </div>

          <div class="submit">
            <button class="btn primary" type="submit" :disabled="!canSubmit || busy">
              {{ busy ? '正在改…' : '改好并重新送审' }}
            </button>
            <p v-if="!readyHint" class="hint">改完之后会重新走一遍审核，通过之后别人才看得到新内容。</p>
            <p v-else class="hint">{{ readyHint }}</p>
          </div>

          <p v-if="error" class="error" role="alert">{{ error }}</p>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import AppIcon from '../components/AppIcon.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import { dotDate } from '../lib/format.js'
import * as api from '../api/index.js'

const route = useRoute()
const router = useRouter()

const no = computed(() => String(route.params.no || ''))
const today = new Date().toISOString().slice(0, 10)

const MIN_FULL_CHARS = 15
const MAX_FULL_CHARS = 400

const SOUND_TYPES = [
  { value: 'ENVIRONMENT', label: '环境声', note: '雨、风、街道、机器 —— 没有人声或只有环境里的人声' },
  { value: 'SPEECH', label: '说话', note: '一段对话、一句话、一段讲述' },
  { value: 'SINGING', label: '演唱', note: '唱出来的。会转人工复审，慢一点，但会有人认真听' }
]

const STATUS = {
  PASS: '已入馆',
  REVIEW: '等人工看一眼',
  REJECT: '没通过',
  PENDING: '审核中'
}

const OUTCOMES = {
  PASS: {
    eyebrow: '改好了，已经入馆',
    said: '机器看过，没问题。编号没变，别处打开的旧链接也还是这一件。'
  },
  REVIEW: {
    eyebrow: '转人工复审了',
    said: '机器拿不准 —— 这不是拒绝，只是需要一个人认真听一遍。通过之后它会自己回到馆藏。'
  },
  REJECT: {
    eyebrow: '这次没通过',
    said: '它现在不在公开馆藏里。看看下面的原因，改一改再交一次。'
  },
  PENDING: {
    eyebrow: '正在审核',
    said: '收到了，还在过审核。'
  }
}

const { data: source, loading, error: loadError, run: load } = useAsync(() =>
  api.uploadSource(no.value)
)

const form = reactive({
  title: '',
  full: '',
  place: '',
  recordedOn: today,
  soundType: 'ENVIRONMENT',
  originalConfirmed: false
})

const busy = ref(false)
const error = ref('')
const result = ref(null)

// 取回原始字段之后回填一次。用户在表单里改过的内容不会被这次回填覆盖，
// 所以只在还没填过的时候写进去。
let filled = false
const fill = () => {
  if (filled || !source.value) return
  filled = true
  form.title = source.value.title || ''
  form.full = source.value.full || ''
  form.place = source.value.place || ''
  form.recordedOn = source.value.date || today
  form.soundType = source.value.soundType || 'ENVIRONMENT'
  form.originalConfirmed = !!source.value.originalConfirmed
}

watch(source, fill, { immediate: true })

const canSubmit = computed(
  () =>
    form.title.trim().length > 0 &&
    form.full.trim().length >= MIN_FULL_CHARS &&
    form.full.trim().length <= MAX_FULL_CHARS &&
    form.place.trim().length > 0 &&
    !!form.recordedOn &&
    form.recordedOn <= today &&
    form.originalConfirmed
)

const readyHint = computed(() => {
  if (!form.title.trim()) return '还差一个名字。'
  if (form.full.trim().length < MIN_FULL_CHARS) {
    return `故事还差 ${MIN_FULL_CHARS - form.full.trim().length} 个字。`
  }
  if (!form.place.trim()) return '还差一个地点。'
  if (!form.recordedOn || form.recordedOn > today) return '录制日期要填成一个过去的日子。'
  if (!form.originalConfirmed) return '还差那个勾。'
  return ''
})

async function submit() {
  if (!canSubmit.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    result.value = await api.editExhibit(no.value, {
      title: form.title.trim(),
      full: form.full.trim(),
      place: form.place.trim(),
      recordedOn: form.recordedOn,
      soundType: form.soundType,
      originalConfirmed: form.originalConfirmed
    })
  } catch (e) {
    // 没改成也不能把内容弄丢：表单还在，用户改的文字还在
    error.value = e?.message || '没能改成功，再试一次。'
  } finally {
    busy.value = false
  }
}

function backToForm() {
  result.value = null
  error.value = ''
}
</script>

<style scoped>
.edit {
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

.lede.note {
  color: var(--ink-4);
}

.board {
  display: grid;
  grid-template-columns: minmax(0, 0.78fr) minmax(0, 1.22fr);
  gap: 32px 64px;
  align-items: start;
}

.artifact {
  position: sticky;
  top: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 330px;
  padding: 24px 0;
}

.well {
  position: absolute;
  inset: 0;
  margin: auto;
  width: min(100%, 300px);
  aspect-ratio: 1;
  border: 1px solid rgba(198, 218, 255, 0.13);
  border-radius: 50%;
  pointer-events: none;
}

.well::before,
.well::after,
.well span {
  content: '';
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(198, 218, 255, 0.09);
}

.well::before {
  inset: 14%;
}

.well::after {
  inset: 30%;
}

.well span {
  inset: 44%;
}

.face {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
  text-align: center;
}

.invite {
  font-size: clamp(17px, 1.7vw, 21px);
  font-weight: 300;
  color: var(--ink-2);
}

.player {
  width: 100%;
  max-width: 300px;
  height: 34px;
}

.state {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--ink-4);
}

.state.is-pass {
  color: var(--brass);
}

.state.is-reject {
  color: #d98b7c;
}

.why {
  max-width: 32ch;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-4);
}

/* ---------- 牌面 ---------- */

.plate {
  border-top: 1px solid rgba(201, 169, 107, 0.42);
  border-bottom: 1px solid var(--line);
  padding: 26px 32px 32px;
}

.row {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 26px;
  padding: 24px 0;
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

.types {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type {
  display: grid;
  grid-template-columns: 16px auto;
  align-items: baseline;
  gap: 4px 12px;
  font-size: 14px;
  font-weight: 300;
  color: var(--ink-2);
  cursor: pointer;
}

.type input {
  width: 15px;
  height: 15px;
  accent-color: var(--brass);
  cursor: pointer;
}

.type-name {
  color: var(--ink);
}

.type-note {
  grid-column: 2;
  font-size: 11px;
  line-height: 1.8;
  color: var(--ink-4);
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

.error {
  margin-top: 20px;
  padding: 13px 16px;
  border: 1px solid rgba(217, 139, 124, 0.34);
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.9;
  color: #e0a093;
}

/* ---------- 改完之后 ---------- */

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

.done .why {
  max-width: none;
  font-size: 13px;
  color: var(--ink-3);
}

.done .acts {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.is-brass {
  color: var(--brass);
}

@media (max-width: 880px) {
  .board {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .artifact {
    position: static;
    min-height: 300px;
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
