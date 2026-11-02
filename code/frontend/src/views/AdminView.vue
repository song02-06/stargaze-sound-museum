<template>
  <div class="admin">
    <header class="bar">
      <div class="brand">
        <span class="mark serif">星轨</span>
        <span class="sep">·</span>
        <span class="title">复审台</span>
      </div>

      <div class="stats" v-if="unlocked">
        <span><b class="tnum">{{ queue.length }}</b> 待复审</span>
        <span class="dot">·</span>
        <span><b class="tnum">{{ records.length }}</b> 条流水</span>
        <span class="dot">·</span>
        <span class="live" :class="{ 'is-on': polling }">
          {{ polling ? '同步中' : '每 5 秒自动同步' }}
        </span>
      </div>

      <div class="tools">
        <RouterLink v-if="unlocked" class="link" to="/">← 回到馆里</RouterLink>
        <button v-if="unlocked" class="link" type="button" @click="lock">退出</button>
      </div>
    </header>

    <!-- 口令闸门 -->
    <section v-if="!unlocked" class="gate">
      <h1 class="serif">复审台</h1>
      <p class="gate-lede">
        这里能替作者决定一段声音能不能公开，所以需要口令。
        口令在服务端的 <code>ADMIN_TOKEN</code> 里，只存在这个标签页。
      </p>

      <form class="gate-form" @submit.prevent="unlock">
        <input
          v-model="tokenDraft"
          class="gate-input"
          type="password"
          placeholder="管理口令"
          autocomplete="off"
          :disabled="checking"
        />
        <button class="btn primary" type="submit" :disabled="checking || !tokenDraft">
          {{ checking ? '核对中…' : '进入' }}
        </button>
      </form>

      <p v-if="gateError" class="gate-error" role="alert">{{ gateError }}</p>
      <p v-if="gateHint" class="gate-hint">{{ gateHint }}</p>
    </section>

    <!-- 工作区 -->
    <div v-else class="workspace">
      <section class="pane queue-pane">
        <div class="pane-head">
          <h2>复审队列</h2>
          <span class="hint">队列会自己更新，不用手动刷新</span>
          <button class="ghost" type="button" :disabled="queueLoading" @click="refresh(false)">
            {{ queueLoading ? '刷新中…' : '刷新' }}
          </button>
        </div>

        <p v-if="pollNote" class="poll-note" role="status">{{ pollNote }}</p>

        <StateNote v-if="queueLoading && !queue.length">正在取队列…</StateNote>
        <StateNote v-else-if="queueError" error :on-retry="() => refresh(false)">{{ queueError }}</StateNote>
        <StateNote v-else-if="!queue.length">
          队列是空的。规则路和人工拦截下来的内容都会先到这里。
        </StateNote>

        <ul v-else class="queue">
          <li v-for="item in queue" :key="`${item.type}-${item.id}`" class="item">
            <div class="item-head">
              <span class="chip" :class="item.type">{{ item.type === 'exhibit' ? '展品' : '回音' }}</span>
              <span class="chip kind">{{ kindLabel(item) }}</span>
              <span class="item-title">{{ item.title }}</span>
              <time class="tnum">{{ agoLabel(item.createdAt) }}</time>
            </div>

            <p v-if="item.reason" class="reason">{{ item.reason }}</p>

            <!-- 语境：判断一段声音该不该公开，光看文字不够 -->
            <p v-if="item.place || item.date" class="meta">
              <span v-if="item.place">{{ item.place }}</span>
              <span v-if="item.place && item.date" class="dot">·</span>
              <span v-if="item.date">{{ item.date }}</span>
            </p>

            <!-- 音频：语音回音转写失败时，只能靠听。没有这个口子就等于没法审 -->
            <div v-if="item.audioUrl" class="audio">
              <audio :src="item.audioUrl" controls preload="none" />
              <span v-if="item.seconds" class="tnum secs">{{ item.seconds }} 秒</span>
            </div>
            <p v-else class="no-audio">这一项没有音频（文字投稿）。</p>

            <p class="content" :class="{ collapsed: !expanded.has(keyOf(item)) }">
              {{ item.text }}
            </p>
            <button
              v-if="item.text && item.text.length > 90"
              class="ghost more"
              type="button"
              @click="toggleExpand(keyOf(item))"
            >
              {{ expanded.has(keyOf(item)) ? '收起' : '展开全文' }}
            </button>

            <div v-if="rejecting === keyOf(item)" class="reject-row">
              <input
                v-model="rejectReason"
                class="reject-input"
                placeholder="驳回原因（必填，会记进流水并发给作者）"
                maxlength="100"
                @keydown.enter.prevent="decide(item, false)"
                @keydown.esc="rejecting = ''"
              />
              <button
                class="act no"
                type="button"
                :disabled="!rejectReason.trim() || busy === keyOf(item)"
                @click="decide(item, false)"
              >
                {{ rejectReason.trim() ? '确认驳回' : '先写原因' }}
              </button>
              <button class="act ghost" type="button" @click="rejecting = ''">取消</button>
            </div>

            <div v-else class="actions">
              <button class="act ghost" type="button" @click="startReject(item)">驳回</button>
              <button class="act ok" type="button" :disabled="busy === keyOf(item)" @click="decide(item, true)">
                通过
              </button>
            </div>
          </li>
        </ul>
      </section>

      <aside class="pane log-pane">
        <div class="pane-head">
          <h2>审核流水</h2>
          <span class="hint">最新在前</span>
        </div>

        <StateNote v-if="recordsLoading && !records.length">正在取流水…</StateNote>
        <StateNote v-else-if="recordsError" error :on-retry="() => refreshRecords(false)">
          {{ recordsError }}
        </StateNote>
        <StateNote v-else-if="!records.length">还没有流水。</StateNote>

        <ol v-else class="log">
          <li v-for="r in records.slice(0, 40)" :key="r.id">
            <span class="stage" :class="r.stage.toLowerCase()">{{ r.stage }}</span>
            <span class="verdict" :class="r.verdict.toLowerCase()">{{ verdictLabel(r.verdict) }}</span>
            <span class="tnum cost">{{ r.costMs }}ms</span>
            <span class="detail">{{ r.detail || '—' }}</span>
          </li>
        </ol>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import StateNote from '../components/StateNote.vue'
import { agoLabel } from '../lib/format.js'
import { usePolling } from '../composables/usePolling.js'
import * as api from '../api/index.js'

const unlocked = ref(false)
const checking = ref(false)
const tokenDraft = ref('')
const gateError = ref('')
const gateHint = ref('')

const queue = ref([])
const records = ref([])
const queueLoading = ref(false)
const recordsLoading = ref(false)
const queueError = ref('')
const recordsError = ref('')

const expanded = reactive(new Set())
const rejecting = ref('')
const rejectReason = ref('')
const busy = ref('')

const keyOf = (item) => `${item.type}-${item.id}`

function verdictLabel(v) {
  return { PASS: '通过', REVIEW: '转人工', REJECT: '驳回' }[v] || v
}

/** 分类/形态：展品看投稿分类，回音看是文字还是语音 */
function kindLabel(item) {
  const map = {
    ENVIRONMENT: '环境声',
    SPEECH: '说话',
    SINGING: '演唱',
    TEXT: '文字回音',
    VOICE: '语音回音'
  }
  return map[item.kind] || item.kind || '—'
}

/* ---------- 取数 ---------- */

async function refreshRecords(silent = true) {
  if (!silent) recordsLoading.value = true
  try {
    records.value = (await api.adminAuditRecords()) || []
    recordsError.value = ''
  } catch (e) {
    recordsError.value = e?.message || '取不到流水'
    if (silent) throw e
  } finally {
    if (!silent) recordsLoading.value = false
  }
}

async function refresh(silent = true) {
  if (!unlocked.value) return
  if (!silent) queueLoading.value = true
  try {
    queue.value = (await api.adminQueue()) || []
    queueError.value = ''
  } catch (e) {
    queueError.value = e?.message || '取不到队列'
    if (silent) throw e
  } finally {
    if (!silent) queueLoading.value = false
  }
  await refreshRecords(silent)
}

const { polling, lastError: pollNote, start, refreshNow } = usePolling(() => refresh(true), {
  interval: 5000
})

/** 用一次真实请求来验口令 —— 而不是把口令拿来跟什么东西对比 */
async function unlock() {
  if (!tokenDraft.value) return
  checking.value = true
  gateError.value = ''
  gateHint.value = ''
  api.setAdminToken(tokenDraft.value)
  try {
    await api.adminQueue()
    unlocked.value = true
    tokenDraft.value = ''
    await refresh(false)
    // 口令对了才开始自动同步
    start()
  } catch (e) {
    api.setAdminToken('')
    gateError.value = e?.message || '进不去'
    if (/没有启用/.test(gateError.value)) {
      gateHint.value =
        '服务端没配 ADMIN_TOKEN。在服务器环境文件里加一行 ADMIN_TOKEN，或者本地 application-local.yml 里配好再重启。'
    }
  } finally {
    checking.value = false
  }
}

function lock() {
  api.setAdminToken('')
  unlocked.value = false
  queue.value = []
  records.value = []
}

function toggleExpand(key) {
  if (expanded.has(key)) expanded.delete(key)
  else expanded.add(key)
}

function startReject(item) {
  rejecting.value = keyOf(item)
  rejectReason.value = ''
}

/**
 * 通过 / 驳回。
 *
 * 驳回必须写原因：按钮在没写原因时是禁用的，服务端也会再校验一次。
 * 队列是轮询来的快照 —— 别人已经决定过的那条，服务端会拒绝，
 * 这里把「刷新一下队列」说清楚，并把队列重新取一遍。
 */
async function decide(item, pass) {
  const key = keyOf(item)
  if (busy.value === key) return
  busy.value = key
  queueError.value = ''
  try {
    await api.adminReview(item.type, item.id, pass, pass ? '' : rejectReason.value.trim())
    queue.value = queue.value.filter((q) => keyOf(q) !== key)
    rejecting.value = ''
    rejectReason.value = ''
    await refreshRecords(false)
  } catch (e) {
    queueError.value = e?.message || '这次决定没记上'
    if (/刷新/.test(queueError.value)) {
      await refresh(true).catch(() => {})
    }
  } finally {
    busy.value = ''
  }
}

onMounted(async () => {
  if (!api.adminToken()) return
  // 口令可能已经失效（或者服务端重启换了口令），所以还是要真请求一次
  try {
    await api.adminQueue()
    unlocked.value = true
    await refresh(false)
    start()
  } catch {
    api.setAdminToken('')
  }
})
</script>

<style scoped>
.admin {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--void);
}

/* ---------- 顶栏 ---------- */

.bar {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 64px;
  padding: 0 32px;
  border-bottom: 1px solid var(--line);
  flex: none;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 14px;
}

.mark {
  font-weight: 500;
  color: var(--ink);
  letter-spacing: 0.18em;
}

.sep {
  color: var(--ink-4);
}

.title {
  color: var(--ink-2);
  letter-spacing: 0.10em;
}

.stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--ink-3);
}

.stats b {
  font-weight: 500;
  color: var(--ink);
}

.dot {
  color: var(--ink-4);
}

.live {
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--ink-4);
}

.live.is-on {
  color: var(--brass);
}

.tools {
  margin-left: auto;
  display: flex;
  gap: 20px;
}

.link {
  font-size: 12px;
  letter-spacing: 0.10em;
  color: var(--ink-3);
  background: none;
}

.link:hover {
  color: var(--brass);
}

/* ---------- 口令闸门 ---------- */

.gate {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 40px 24px 120px;
  text-align: center;
}

.gate h1 {
  font-size: 28px;
  font-weight: 400;
  letter-spacing: 0.08em;
}

.gate-lede {
  max-width: 32rem;
  font-size: 13px;
  line-height: 2;
  color: var(--ink-3);
}

.gate-lede code {
  padding: 2px 6px;
  border: 1px solid var(--line-2);
  border-radius: 4px;
  font-family: var(--mono);
  font-size: 12px;
  color: var(--ink-2);
}

.gate-form {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}

.gate-input {
  width: 22rem;
  height: 50px;
  padding: 0 18px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  font-size: 14px;
  letter-spacing: 0.06em;
  color: var(--ink);
}

.gate-input::placeholder {
  color: var(--ink-4);
}

.gate-input:focus {
  border-color: var(--brass-2);
  outline: none;
}

.gate-error {
  font-size: 13px;
  color: #d98b7c;
}

.gate-hint {
  max-width: 34rem;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-4);
}

/* ---------- 工作区 ---------- */

.workspace {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 400px);
  gap: 0;
  min-height: 0;
}

.pane {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 32px 40px;
  overflow-y: auto;
}

.log-pane {
  border-left: 1px solid var(--line);
}

.pane-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  background: var(--void);
  z-index: 1;
}

.pane-head h2 {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.18em;
  color: var(--ink);
}

.hint {
  font-size: 11px;
  color: var(--ink-4);
}

.ghost {
  margin-left: auto;
  font-size: 12px;
  letter-spacing: 0.10em;
  color: var(--ink-3);
}

.ghost:hover:not(:disabled) {
  color: var(--brass);
}

.poll-note {
  margin-top: 12px;
  font-size: 11px;
  color: var(--ink-4);
}

/* ---------- 队列 ---------- */

.queue {
  list-style: none;
}

.item {
  padding: 22px 0;
  border-bottom: 1px solid var(--line);
}

.item-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 10px;
}

.chip {
  flex: none;
  padding: 3px 9px;
  border: 1px solid var(--line-2);
  border-radius: 4px;
  font-size: 11px;
  letter-spacing: 0.10em;
  color: var(--ink-3);
}

.chip.exhibit {
  border-color: var(--brass-2);
  color: var(--brass);
}

.chip.kind {
  border-color: transparent;
  color: var(--ink-4);
  padding-inline: 0;
}

.item-title {
  font-size: 14px;
  color: var(--ink);
}

.item-head time {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-4);
}

.reason {
  margin-top: 12px;
  padding: 8px 12px;
  border-left: 1px solid var(--brass-2);
  font-size: 12px;
  line-height: 1.9;
  color: var(--brass);
}

.meta {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--ink-4);
}

.audio {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 14px;
}

.audio audio {
  width: min(100%, 420px);
  height: 34px;
}

.secs {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--ink-4);
}

.no-audio {
  margin-top: 12px;
  font-size: 11px;
  color: var(--ink-4);
}

.content {
  margin-top: 14px;
  max-width: 46rem;
  font-size: 14px;
  font-weight: 300;
  line-height: 2;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.content.collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.more {
  margin: 8px 0 0;
  padding: 0;
}

.actions,
.reject-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

.act {
  height: 38px;
  padding: 0 22px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  font-size: 13px;
  letter-spacing: 0.10em;
  color: var(--ink-2);
  transition: border-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.act:hover:not(:disabled) {
  color: var(--ink);
  border-color: rgba(255, 255, 255, 0.3);
}

.act.ok {
  border-color: var(--brass-2);
  color: var(--brass);
}

.act.ok:hover:not(:disabled) {
  border-color: var(--brass);
  color: #e0c185;
}

.act.no {
  border-color: rgba(217, 139, 124, 0.45);
  color: #d98b7c;
}

.act.no:disabled {
  opacity: 0.45;
}

.act.ghost {
  border-color: transparent;
  color: var(--ink-4);
  padding-inline: 10px;
}

.reject-input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line-2);
  border-radius: 8px;
  font-size: 13px;
  color: var(--ink);
}

.reject-input::placeholder {
  color: var(--ink-4);
}

/* ---------- 流水 ---------- */

.log {
  list-style: none;
}

.log li {
  display: grid;
  grid-template-columns: 52px 54px 62px minmax(0, 1fr);
  align-items: baseline;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}

.stage {
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.10em;
  color: var(--ink-3);
}

.stage.asr {
  color: #8fb4d6;
}

.stage.manual {
  color: var(--brass);
}

.verdict {
  font-size: 11px;
  color: var(--ink-4);
}

.verdict.pass {
  color: #86a98c;
}

.verdict.reject {
  color: #d98b7c;
}

.verdict.review {
  color: var(--brass);
}

.cost {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--ink-4);
  text-align: right;
}

.detail {
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .log-pane {
    border-left: none;
    border-top: 1px solid var(--line);
  }

  .bar {
    padding: 0 20px;
    gap: 16px;
  }

  .stats {
    display: none;
  }

  .pane {
    padding: 20px;
  }

  .gate-input {
    width: 100%;
  }

  .gate-form {
    flex-direction: column;
    width: 100%;
    max-width: 22rem;
  }

  .log li {
    grid-template-columns: 48px 50px 56px minmax(0, 1fr);
  }
}
</style>
