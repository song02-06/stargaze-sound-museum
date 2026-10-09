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
        <StateNote v-if="echoError" error :on-retry="loadEchoes">{{ echoError }}</StateNote>
        <EchoList v-else :items="list || []" />
      </section>

      <footer class="rail composer-area">
        <StateNote v-if="postError" error>{{ postError }}</StateNote>
        <EchoComposer :busy="submitting" @submit="onSubmit" />
      </footer>

      <audio :ref="bind" :src="exhibit.audioUrl" preload="metadata" />
    </template>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import WaveTrace from '../components/WaveTrace.vue'
import EchoList from '../components/EchoList.vue'
import EchoComposer from '../components/EchoComposer.vue'
import AppIcon from '../components/AppIcon.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import * as api from '../api/index.js'
import { usePlayer } from '../composables/usePlayer.js'
import { clock } from '../lib/format.js'

const route = useRoute()
const { data: exhibit, loading, error, run } = useAsync(() => api.exhibit(route.params.id))
const { data: list, error: echoError, run: loadEchoes } = useAsync(() => api.echoes(route.params.id))

const { bind, playing, duration, progress, toggle } = usePlayer()

const submitting = ref(false)
const postError = ref('')

async function onSubmit(echo) {
  submitting.value = true
  postError.value = ''
  try {
    // 回音交给后端审核：文字过敏感词，语音读取真实时长并抽波形。
    // 前端不自己判断能不能发 —— 规则在服务端，客户端绕不过去。
    await api.postEcho(route.params.id, echo)
    await loadEchoes()
  } catch (e) {
    postError.value = e?.message || '这条回音没能发出去'
  } finally {
    submitting.value = false
  }
}
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

.composer-area {
  margin-top: auto;
  padding-top: 48px;
  padding-bottom: 52px;
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
