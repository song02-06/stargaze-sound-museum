<template>
  <div class="mine">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
        <RouterLink class="is-current" to="/mine">我的声音</RouterLink>
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
      <p class="lede">
        昵称只存在这台设备上。没有邮箱、没有密码，也没有头像 ——
        <b>你在馆里有个名字，但没人知道你是谁。</b>
      </p>
    </header>

    <div class="rail">
      <StateNote v-if="loading">正在整理…</StateNote>
      <StateNote v-else-if="error" error :on-retry="run">{{ error }}</StateNote>
    </div>

    <template v-if="!loading && !error">
      <section class="rail board">
        <p class="eyebrow">你埋下的声音</p>
        <ul v-if="uploads.length" class="buried">
          <li v-for="item in uploads" :key="item.no">
            <!-- 问之前先把后果说清楚：它下面挂着几条别人的回音 -->
            <div v-if="confirmNo === item.no" class="held ask">
              <span class="no tnum">Nº {{ item.no }}</span>
              <span class="serif title">{{ item.title }}</span>
              <span class="said">
                删掉它？{{
                  item.echoCount
                    ? `它下面有 ${item.echoCount} 条回音，会一起消失。`
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
              <button class="btn quiet del" type="button" @click="confirmNo = item.no">删除</button>
            </template>
          </li>
        </ul>
        <p v-else class="empty">
          你还没埋下过声音。<RouterLink class="inline" to="/bury">埋一段试试。</RouterLink>
        </p>
        <p v-if="removeError" class="foot-note is-error" role="alert">{{ removeError }}</p>
        <p v-else-if="removedNote" class="foot-note">{{ removedNote }}</p>
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
      <p>回音与用户数据均为演示内容，不代表任何真实用户</p>
    </footer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import * as api from '../api/index.js'
import { nickname, setNickname } from '../store/museum.js'

const { data: mine, loading, error, run } = useAsync(() => api.mine())
const kept = computed(() => mine.value?.kept || [])
const uploads = computed(() => mine.value?.uploads || [])

// 状态用产品自己的话，不用英文枚举
const STATUS = {
  PASS: '已入馆',
  REVIEW: '等人工看一眼',
  REJECT: '没通过',
  PENDING: '审核中'
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
    confirmNo.value = null
    removedNote.value = `Nº ${item.no} 已经删掉了。`
  } catch (e) {
    removeError.value = e?.message || '没能删掉，再试一次。'
  } finally {
    removing.value = false
  }
}

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

/* 埋下的那几段可以删 —— 但删之前必须把后果说清楚 */
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
