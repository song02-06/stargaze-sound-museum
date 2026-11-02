<template>
  <div class="mine">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
      </template>
    </AppTopBar>

    <header class="head rail">
      <p class="eyebrow">我的声音</p>
      <h1 class="serif">
        你在馆里叫
        <input
          v-if="editOpen"
          v-model="draft"
          class="nick-input"
          maxlength="12"
          aria-label="昵称"
          @keydown.enter.prevent="commit"
          @blur="commit"
        />
        <button v-else class="nick" type="button" @click="edit">{{ nickname || '访客' }}</button>
      </h1>
      <p v-if="editError" class="edit-error">{{ editError }}</p>
      <p class="lede" v-if="loggedIn">已登录账号 {{ state.session.username }}。在其他设备登录，即可找回你的声音和回音。</p>
      <p class="lede" v-else>当前为游客身份，内容保存在当前浏览器身份下。注册账号后，可在其他设备登录找回。</p>
      <div class="account-actions">
        <button v-if="loggedIn" class="btn quiet" type="button" :disabled="loggingOut" @click="signOut">{{ loggingOut ? '正在退出…' : '退出登录' }}</button>
        <RouterLink v-else class="btn primary" to="/login">登录 / 注册</RouterLink>
        <p v-if="logoutError" role="alert">{{ logoutError }}</p>
      </div>
    </header>

    <div class="rail">
      <StateNote v-if="loading">正在整理…</StateNote>
      <StateNote v-else-if="error" error :on-retry="load">{{ error }}</StateNote>
      <p v-else-if="pollError" class="foot-note is-error" role="status">
        这次没能刷出新消息（{{ pollError }}）。屏幕上还是上一次的内容。
      </p>
    </div>

    <template v-if="!loading && !error">
      <!-- ---------- 审核消息：状态变化的回执 ---------- -->
      <section v-if="notifications.length" class="rail board">
        <p class="eyebrow">
          消息
          <span v-if="unread" class="unread tnum">{{ unread }} 条未读</span>
        </p>
        <ul class="notes">
          <li v-for="n in notifications" :key="n.id" :class="{ 'is-unread': !n.read }">
            <button class="note" type="button" @click="openNotification(n)">
              <span class="dot" aria-hidden="true" />
              <span class="note-text">{{ n.summary }}</span>
              <span class="when">{{ agoLabel(n.createdAt) }}</span>
              <span v-if="n.targetNo" class="go">去看 Nº {{ n.targetNo }}</span>
            </button>
          </li>
        </ul>
      </section>

      <!-- ---------- 回音收件箱：我的展品收到的公开回音 ---------- -->
      <section v-if="inbox.length" class="rail board">
        <p class="eyebrow">回音收件箱 · {{ inbox.length }} 条</p>
        <p class="sub-lede">
          这是别人发给<b>你已经入馆的</b>那些声音的公开回音。
          没有私信 —— 这里只是把它们汇总到一处。
        </p>
        <EchoList :items="inboxAsEchoes" />
      </section>

      <!-- ---------- 我埋下的声音 ---------- -->
      <section class="rail board">
        <p class="eyebrow">你埋下的声音</p>
        <ul v-if="uploads.length" class="buried">
          <li v-for="item in uploads" :key="item.no">
            <!-- 问之前先把后果说清楚：它下面挂着几条回音 -->
            <div v-if="confirmNo === item.no" class="held ask">
              <span class="no tnum">Nº {{ item.no }}</span>
              <span class="serif title">{{ item.title }}</span>
              <span class="said">
                删掉它？{{
                  item.echoCount
                    ? `它下面有 ${item.echoCount} 条回音${
                        item.publicEchoCount < item.echoCount
                          ? `（其中 ${item.publicEchoCount} 条已公开）`
                          : ''
                      }，会一起消失。审核流水会留下。`
                    : '删掉之后就找不回来了。'
                }}
              </span>
              <span class="acts">
                <button class="btn quiet danger" type="button" :disabled="removing" @click="remove(item)">
                  {{ removing ? '正在删…' : '删掉' }}
                </button>
                <button class="btn quiet" type="button" @click="confirmNo = null">算了</button>
              </span>
            </div>

            <template v-else>
              <RouterLink v-if="item.status === 'PASS'" :to="`/exhibit/${item.no}`">
                <span class="no tnum">Nº {{ item.no }}</span>
                <span class="serif title">{{ item.title }}</span>
                <span class="state is-pass">已入馆</span>
              </RouterLink>
              <div v-else class="held">
                <span class="no tnum">Nº {{ item.no }}</span>
                <span class="serif title">{{ item.title }}</span>
                <span class="state" :class="`is-${item.status.toLowerCase()}`">
                  {{ STATUS[item.status] || item.status }}
                </span>
                <span v-if="item.auditNote" class="why">{{ item.auditNote }}</span>
              </div>
              <span class="row-actions">
                <RouterLink class="btn quiet mini-btn" :to="`/mine/uploads/${item.no}/edit`">改一改</RouterLink>
                <button class="btn quiet del" type="button" @click="confirmNo = item.no">删除</button>
              </span>
            </template>
          </li>
        </ul>
        <p v-else class="empty">
          你还没埋下过声音。<RouterLink class="inline" to="/bury">埋一段试试。</RouterLink>
        </p>
        <p v-if="removeError" class="foot-note is-error" role="alert">{{ removeError }}</p>
        <p v-else-if="removedNote" class="foot-note">{{ removedNote }}</p>
      </section>

      <!-- ---------- 我发出的回音 ---------- -->
      <section class="rail board">
        <p class="eyebrow">我发出的回音</p>
        <ul v-if="myEchoes.length" class="outbox">
          <li v-for="e in myEchoes" :key="e.id">
            <RouterLink v-if="e.exhibitNo" class="to" :to="`/exhibit/${e.exhibitNo}`">
              Nº {{ e.exhibitNo }} · 《{{ e.exhibitTitle }}》
            </RouterLink>
            <span class="state" :class="`is-${e.status.toLowerCase()}`">
              {{ STATUS[e.status] || e.status }}
            </span>
            <span v-if="e.auditNote" class="why">{{ e.auditNote }}</span>
            <span class="body">{{ e.kind === 'voice' ? '（语音回音）' : e.body }}</span>
            <span class="when">{{ agoLabel(e.createdAt) }}</span>
          </li>
        </ul>
        <p v-else class="empty">你还没给谁留过回音。</p>
      </section>

      <section class="rail board">
        <p class="eyebrow">你回应过的展品</p>
        <ul v-if="kept.length" class="kept">
          <li v-for="item in kept" :key="item.id">
            <RouterLink :to="`/exhibit/${item.no}`">
              <span class="no tnum">Nº {{ item.no }}</span>
              <span class="serif title">{{ item.title }}</span>
              <span class="count">{{ item.echoCount }} 条回音</span>
            </RouterLink>
          </li>
        </ul>
        <p v-else class="empty">
          你还没给谁留过回音。去
          <RouterLink class="inline" to="/">打捞一段</RouterLink>
          试试。
        </p>
      </section>
    </template>

    <footer class="foot rail">
      <p>
        这里只显示<b>你自己的</b>投稿、回音与消息。
        馆里标着「演示」的那几件是合成占位素材，不是你留下的。
      </p>
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import StateNote from '../components/StateNote.vue'
import EchoList from '../components/EchoList.vue'
import { usePolling } from '../composables/usePolling.js'
import { agoLabel } from '../lib/format.js'
import * as api from '../api/index.js'
import { nickname, setNickname, loggedIn, state, logout } from '../store/museum.js'

const loggingOut = ref(false)
const logoutError = ref('')
async function signOut() {
  if (loggingOut.value) return
  loggingOut.value = true
  logoutError.value = ''
  try { await logout(); window.location.assign('/'); }
  catch (e) { logoutError.value = e.message || '退出失败，请重试'; }
  finally { loggingOut.value = false }
}

const router = useRouter()

const mine = ref(null)
const loading = ref(true)
const error = ref('')

async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    mine.value = await api.mine()
    error.value = ''
  } catch (e) {
    // 首次加载失败要让人看到并能重试；轮询失败保留已有内容
    if (!silent) error.value = e?.message || '加载失败'
    else throw e
  } finally {
    if (!silent) loading.value = false
  }
}

const uploads = computed(() => mine.value?.uploads || [])
const kept = computed(() => mine.value?.kept || [])
const myEchoes = computed(() => mine.value?.echoes || [])
const inbox = computed(() => mine.value?.inbox || [])
const notifications = computed(() => mine.value?.notifications || [])
const unread = computed(() => mine.value?.unread || 0)

/** 收件箱里的回音复用 EchoList 的排版，只要把 echoId 映射成 id */
const inboxAsEchoes = computed(() =>
  inbox.value.map((item) => ({ ...item, id: item.echoId }))
)

// 状态用产品自己的话，不用英文枚举
const STATUS = {
  PASS: '已入馆',
  REVIEW: '等人工看一眼',
  REJECT: '没通过',
  PENDING: '审核中'
}

/* ---------- 自动更新：每 5 秒、上一次回来再排下一次 ---------- */
const { lastError: pollError, start, refreshNow } = usePolling(() => load({ silent: true }))

onMounted(async () => {
  try {
    await load()
  } catch {
    // 首屏失败已经写进 error
  } finally {
    // 首屏成功之后才开轮询，避免还没连上就连环重试
    if (!error.value) start()
  }
})

/* ---------- 消息 ---------- */

async function openNotification(n) {
  try {
    if (!n.read) {
      await api.markNotificationRead(n.id)
      n.read = true
      if (mine.value) mine.value.unread = Math.max(0, unread.value - 1)
    }
  } catch {
    // 标记失败也让他点进去：消息内容本身是能看的
  }
  if (n.targetNo) router.push(`/exhibit/${n.targetNo}`)
}

/* ---------- 删掉自己埋下的一段 ---------- */

const confirmNo = ref(null)
const removing = ref(false)
const removedNote = ref('')
const removeError = ref('')

async function remove(item) {
  if (removing.value) return
  removing.value = true
  removeError.value = ''

  try {
    await api.deleteExhibit(item.no)
    // 本地直接摘掉，不重跑整页 —— 列表闪一下比删除本身还显眼
    mine.value.uploads = mine.value.uploads.filter((u) => u.no !== item.no)
    mine.value.kept = (mine.value.kept || []).filter((k) => k.no !== item.no)
    confirmNo.value = null
    removedNote.value = `Nº ${item.no} 已经删掉了，相关的回音和音频一起清掉了。`
    refreshNow()
  } catch (e) {
    removeError.value = e?.message || '没能删掉，再试一次。'
  } finally {
    removing.value = false
  }
}

/* ---------- 昵称 ---------- */

const editOpen = ref(false)
const draft = ref('')
const editError = ref('')

function edit() {
  draft.value = nickname.value
  editError.value = ''
  editOpen.value = true
  // 用原生 prompt 会打断全屏观感；这里换成页内输入
  window.setTimeout(() => {
    document.querySelector('.nick-input')?.focus()
  }, 0)
}

async function commit() {
  try {
    await setNickname(draft.value)
    editOpen.value = false
  } catch (e) {
    editError.value = e?.message || '没改成'
  }
}
</script>

<style scoped>
.account-actions { margin-top: 22px; }
.mine {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  padding-bottom: 40px;
}

.head {
  padding-top: 60px;
  padding-bottom: 40px;
}

.head h1 {
  margin-top: 18px;
  font-size: clamp(24px, 3vw, 36px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
}

.nick {
  color: var(--brass);
  border-bottom: 1px dashed rgba(201, 169, 107, 0.45);
  font: inherit;
}

.nick-input {
  width: 6ch;
  color: var(--brass);
  border-bottom: 1px solid var(--brass-2);
  font: inherit;
  text-align: center;
}

.edit-error {
  margin-top: 10px;
  font-size: 12px;
  color: #d98b7c;
}

.nick:hover {
  border-bottom-style: solid;
}

.lede {
  margin-top: 16px;
  max-width: var(--measure);
  font-size: 14px;
  font-weight: 300;
  line-height: 2;
  color: var(--ink-3);
}

.lede b {
  font-weight: 400;
  color: var(--ink-2);
}

.board {
  padding-top: 30px;
}

.sub-lede {
  margin-top: 12px;
  max-width: var(--measure);
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-4);
}

.sub-lede b {
  font-weight: 400;
  color: var(--ink-3);
}

/* ---------- 消息 ---------- */

.unread {
  margin-left: 10px;
  color: var(--brass);
}

.notes {
  margin-top: 16px;
  border-top: 1px solid var(--line);
  list-style: none;
}

.notes li {
  border-bottom: 1px solid var(--line);
}

.note {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 14px;
  width: 100%;
  padding: 16px 0;
  text-align: left;
  font-size: 13px;
  color: var(--ink-2);
}

.note:hover .note-text {
  color: var(--ink);
}

.dot {
  flex: none;
  align-self: center;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: transparent;
}

.is-unread .dot {
  background: var(--brass);
}

.note-text {
  flex: 1;
  min-width: 12ch;
  line-height: 1.8;
  transition: color var(--dur-fast) var(--ease);
}

.is-unread .note-text {
  color: var(--ink);
}

.when {
  flex: none;
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--ink-4);
}

.go {
  flex-basis: 100%;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--brass);
}

/* ---------- 我发出的回音 ---------- */

.outbox {
  margin-top: 16px;
  border-top: 1px solid var(--line);
  list-style: none;
}

.outbox li {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 16px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.outbox .to {
  font-size: 13px;
  color: var(--ink);
  border-bottom: 1px solid transparent;
}

.outbox .to:hover {
  border-bottom-color: var(--brass-2);
  text-decoration: none;
}

.outbox .body {
  flex-basis: 100%;
  font-size: 13px;
  font-weight: 300;
  line-height: 1.95;
  color: var(--ink-3);
  overflow-wrap: anywhere;
}

/* ---------- 回应过的展品 ---------- */

.kept {
  margin-top: 16px;
  border-top: 1px solid var(--line);
  list-style: none;
}

.kept a {
  display: flex;
  align-items: baseline;
  gap: 24px;
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}

.kept a:hover {
  text-decoration: none;
}

.no {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--brass);
  flex: none;
}

.title {
  flex: 1;
  font-size: 17px;
  color: var(--ink);
  transition: color var(--dur-fast) var(--ease);
}

.kept a:hover .title {
  color: var(--brass);
}

/* 埋下的那几段：已入馆的能点进去，还在审核的没有链接 —— 共用一套排版 */
.buried {
  margin-top: 16px;
  border-top: 1px solid var(--line);
  list-style: none;
}

.buried a,
.buried .held {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}

.buried a:hover {
  text-decoration: none;
}

.buried a:hover .title {
  color: var(--brass);
}

.state {
  flex: none;
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
  flex-basis: 100%;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-4);
}

.buried li {
  display: flex;
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid var(--line);
}

.buried li > a,
.buried li > .held {
  flex: 1;
  min-width: 0;
  border-bottom: none;
}

.buried .held.ask {
  align-items: baseline;
}

.buried .said {
  flex-basis: 100%;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-3);
}

.buried .acts {
  display: flex;
  gap: 4px;
  flex: none;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.mini-btn {
  font-size: 11px;
  color: var(--brass);
}

.danger {
  color: #d98b7c;
}

.danger:hover:not(:disabled) {
  color: #e8a294;
}

.del {
  flex: none;
  font-size: 11px;
  color: var(--ink-4);
}

.del:hover:not(:disabled) {
  color: #d98b7c;
}

.foot-note {
  margin-top: 16px;
  font-size: 12px;
  color: var(--ink-3);
}

.foot-note.is-error {
  color: #d98b7c;
}

.count {
  font-size: 12px;
  letter-spacing: 0.12em;
  color: var(--ink-4);
}

.empty {
  padding: 32px 0;
  font-size: 14px;
  color: var(--ink-3);
}

.inline {
  color: var(--brass);
  border-bottom: 1px solid var(--brass-2);
}

.foot {
  margin-top: auto;
  padding-top: 48px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--ink-4);
}

.foot b {
  font-weight: 400;
  color: var(--ink-3);
}

@media (max-width: 760px) {
  .head {
    padding-top: 40px;
  }

  .kept a {
    flex-wrap: wrap;
    gap: 8px 16px;
  }
}
</style>
