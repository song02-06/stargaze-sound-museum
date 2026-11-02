<template>
  <div class="echoes">
    <AppTopBar>
      <template #left>
          <RouterLink :to="`/exhibit/${exhibit?.no || ''}`">
          <AppIcon name="chevron-left" :size="13" />回到展品
        </RouterLink>
      </template>
      <template #right>
        <span v-if="exhibit">馆藏 Nº {{ exhibit.no }}</span>
      </template>
    </AppTopBar>

    <div v-if="loading" class="missing rail">
      <p class="serif">正在把这一段提上来…</p>
    </div>

    <div v-else-if="error" class="missing rail">
      <p class="serif">{{ error }}</p>
      <button class="btn" type="button" @click="run">再试一次</button>
    </div>

    <template v-else-if="exhibit">
      <section class="now rail">
        <h1 class="serif">{{ exhibit.title }}</h1>
        <div class="mini">
          <button class="play" type="button" :aria-label="playing ? '暂停' : '播放'" @click="toggle">
            <AppIcon :name="playing ? 'pause' : 'play'" :size="13" />
          </button>
          <WaveTrace class="mini-trace" mini :trace="exhibit.trace" :progress="progress" />
          <span class="time tnum">{{ clock(duration || exhibit.seconds) }}</span>
        </div>
      </section>

      <div class="hairline rail-line" />

      <section class="rail list-area">
        <p class="eyebrow">回音 · {{ (list || []).length }} 条</p>
        <p v-if="pollError" class="poll-note" role="status">这次没能刷出新回音（{{ pollError }}）。</p>
        <StateNote v-if="echoError" error :on-retry="loadEchoes">{{ echoError }}</StateNote>
        <EchoList v-else :items="list || []" />
      </section>

      <footer class="rail composer-area">
        <!-- 发出去之后必须说清楚它现在在哪：已公开 / 等人工 / 被驳回 -->
        <div v-if="submitted" class="result" :class="`is-${String(submitted.status).toLowerCase()}`" role="status">
          <p class="result-head">{{ RESULT[submitted.status]?.head || '发出去了' }}</p>
          <p class="result-said">{{ RESULT[submitted.status]?.said }}</p>
          <p v-if="submitted.auditNote" class="result-why">{{ submitted.auditNote }}</p>
          <RouterLink v-if="submitted.status !== 'PASS'" class="result-link" to="/mine">
            在「我的声音」里看这条回音现在在哪
          </RouterLink>
        </div>

        <StateNote v-if="postError" error>{{ postError }}</StateNote>
        <p v-if="postError" class="keep-note">没发出去的内容还留着，改一改或者等网络好了再点一次发送。</p>

        <EchoComposer :busy="submitting" :reset-token="resetToken" @submit="onSubmit" />
      </footer>

      <audio :ref="bind" :src="exhibit.audioUrl" preload="metadata" />
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import WaveTrace from '../components/WaveTrace.vue'
import EchoList from '../components/EchoList.vue'
import EchoComposer from '../components/EchoComposer.vue'
import AppIcon from '../components/AppIcon.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import { usePolling } from '../composables/usePolling.js'
import * as api from '../api/index.js'
import { usePlayer } from '../composables/usePlayer.js'
import { clock } from '../lib/format.js'

const route = useRoute()
const { data: exhibit, loading, error, run } = useAsync(() => api.exhibit(route.params.id))
const { data: list, error: echoError, run: loadEchoes } = useAsync(() => api.echoes(route.params.id))

const { bind, playing, duration, progress, toggle } = usePlayer()

const submitting = ref(false)
const postError = ref('')
const submitted = ref(null)
/** 父组件确认发送成功之后才自增，输入组件据此清掉草稿 */
const resetToken = ref(0)

const RESULT = {
  PASS: {
    head: '已经公开了',
    said: '它现在就出现在下面这份回音列表里。'
  },
  REVIEW: {
    head: '在等人工看一眼',
    said: '机器拿不准 —— 这不是拒绝。通过之后它会出现在公开列表里。'
  },
  REJECT: {
    head: '这条没通过',
    said: '它不会公开。看看下面的原因，改一改再发一次。'
  },
  PENDING: {
    head: '收到了',
    said: '还在过审核。'
  }
}

async function onSubmit(echo) {
  // 点击、回车、语音三条路都会走到这里，互斥判断放在入口
  if (submitting.value) return
  submitting.value = true
  postError.value = ''
  try {
    // 回音交给后端审核：文字过规则路，语音读真实时长并抽波形。
    // 前端不自己判断能不能发 —— 规则在服务端，客户端绕不过去。
    submitted.value = await api.postEcho(route.params.id, echo)
    // 只有真的成功了才清空输入
    resetToken.value += 1
    await loadEchoes()
  } catch (e) {
    // 失败时输入组件不清空：写过的字、录过的音都还在
    postError.value = e?.message || '这条回音没能发出去'
  } finally {
    submitting.value = false
  }
}

const { lastError: pollError, start } = usePolling(async () => {
  await loadEchoes()
  // useAsync 自己吞掉了异常，这里把失败浮出来，轮询才知道「这次没刷成」
  if (echoError.value) throw new Error(echoError.value)
})

onMounted(() => {
  // 别人的回音也要能在不刷新整页的情况下出现
  start()
})
</script>

<style scoped>
.echoes {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.now {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 24px;
  padding-top: 46px;
  padding-bottom: 30px;
}

.now h1 {
  font-size: clamp(19px, 2vw, 23px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
}

.mini {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 200px;
}

.mini-trace {
  flex: 1;
  min-width: 90px;
  max-width: 220px;
}

.play {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line-2);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.015);
  transition: border-color var(--dur-fast) var(--ease);
}

.play:hover {
  border-color: rgba(255, 255, 255, 0.28);
}

.rail-line {
  margin-inline: var(--rail);
}

.list-area {
  padding-top: 34px;
}

.poll-note {
  margin-top: 10px;
  font-size: 11px;
  color: var(--ink-4);
}

.composer-area {
  margin-top: auto;
  padding-top: 48px;
  padding-bottom: 52px;
}

/* 发送结果：三态用同一块位置，颜色不同 */
.result {
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid var(--line-2);
  border-radius: 12px;
}

.result.is-pass {
  border-color: rgba(201, 169, 107, 0.42);
}

.result.is-reject {
  border-color: rgba(217, 139, 124, 0.34);
}

.result-head {
  font-size: 13px;
  letter-spacing: 0.1em;
  color: var(--ink);
}

.result-said {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-3);
}

.result-why {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.9;
  color: #d98b7c;
}

.result-link {
  display: inline-block;
  margin-top: 8px;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--brass);
  border-bottom: 1px solid var(--brass-2);
}

.keep-note {
  margin-bottom: 14px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--ink-3);
}

.time {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--ink-4);
  white-space: nowrap;
}

.missing {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 26px;
  color: var(--ink-2);
}

@media (max-width: 760px) {
  .now {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
    padding-top: 28px;
  }

  .mini {
    width: 100%;
  }
}
</style>
