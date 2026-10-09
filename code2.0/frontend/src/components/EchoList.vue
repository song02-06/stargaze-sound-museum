<template>
  <div class="list">
    <p v-if="!items.length" class="empty">还没有人回音。你是第一个听到它的人。</p>

    <ul v-else>
      <li v-for="echo in items" :key="echo.id" class="echo">
        <p class="head">
          <span class="who">{{ echo.author }}</span>
          <span class="when">{{ agoLabel(echo.createdAt) }}</span>
          <span v-if="echo.author === nickname" class="mine">你写的</span>
        </p>

        <p v-if="echo.kind === 'text'" class="body">{{ echo.body }}</p>

        <div v-else class="voice">
          <button
            class="play"
            type="button"
            :aria-label="playingId === echo.id ? '暂停这条语音回音' : '播放这条语音回音'"
            @click="toggle(echo)"
          >
            <AppIcon :name="playingId === echo.id ? 'pause' : 'play'" :size="12" />
          </button>
          <WaveTrace
            class="mini"
            mini
            :trace="echo.trace"
            :progress="playingId === echo.id ? progress : 0"
          />
          <span class="time tnum">{{ clock(echo.seconds) }}</span>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import WaveTrace from './WaveTrace.vue'
import AppIcon from './AppIcon.vue'
import { clock, agoLabel } from '../lib/format.js'
import { nickname } from '../store/museum.js'

defineProps({
  items: { type: Array, default: () => [] }
})

const playingId = ref('')
const progress = ref(0)
let audio = null

function toggle(echo) {
  const src = echo.audioUrl
  if (!src) return

  if (playingId.value === echo.id) {
    audio?.pause()
    playingId.value = ''
    progress.value = 0
    return
  }

  audio?.pause()
  audio = new Audio(src)
  audio.addEventListener('timeupdate', () => {
    progress.value = audio.currentTime / (audio.duration || echo.seconds || 1)
  })
  const stop = () => {
    playingId.value = ''
    progress.value = 0
  }
  audio.addEventListener('ended', stop)
  audio.addEventListener('error', stop)
  audio
    .play()
    .then(() => {
      playingId.value = echo.id
      progress.value = 0
    })
    .catch(stop)
}

onUnmounted(() => audio?.pause())
</script>

<style scoped>
.empty {
  padding: 32px 0 8px;
  font-size: 14px;
  color: var(--ink-4);
}

.echo {
  padding: 24px 0;
  border-top: 1px solid var(--line);
}

.echo:first-child {
  border-top: none;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.who {
  font-size: 13px;
  color: var(--ink);
}

.when {
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--ink-4);
}

.mine {
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--brass);
}

.body {
  margin-top: 10px;
  max-width: var(--measure);
  font-size: 14px;
  font-weight: 300;
  line-height: 2.05;
  color: var(--ink-2);
  text-wrap: pretty;
}

.voice {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
  max-width: 24rem;
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

.mini {
  flex: 1;
  min-width: 60px;
}

.time {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--ink-4);
  white-space: nowrap;
}
</style>
