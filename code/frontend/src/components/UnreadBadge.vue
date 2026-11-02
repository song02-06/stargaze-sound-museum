<template>
  <RouterLink v-if="unread > 0" class="badge" to="/mine" :aria-label="`你有 ${unread} 条未读消息`">
    消息<span class="num tnum">{{ unread > 99 ? '99+' : unread }}</span>
  </RouterLink>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import * as api from '../api/index.js'

/**
 * 顶栏未读角标。
 *
 * 只轮询「未读数量」这一个数字（单独一个轻接口），
 * 而且是上一次请求回来之后再排下一次 —— 不会堆请求。
 * 标签页重新可见时补一次：切回来看到的数字要是新的。
 */
const unread = ref(0)

let timer = 0
let stopped = false
let inFlight = false

async function tick() {
  if (stopped || inFlight) return
  inFlight = true
  try {
    const res = await api.unreadCount()
    unread.value = Number(res?.unread) || 0
  } catch {
    // 顶栏是装饰，取不到就保持上一次的数字，不要弹错误打断人
  } finally {
    inFlight = false
    schedule()
  }
}

function schedule() {
  if (stopped) return
  window.clearTimeout(timer)
  timer = window.setTimeout(tick, 5000)
}

function onVisible() {
  if (document.visibilityState === 'visible') tick()
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisible)
  tick()
})

onUnmounted(() => {
  stopped = true
  window.clearTimeout(timer)
  document.removeEventListener('visibilitychange', onVisible)
})
</script>

<style scoped>
.badge {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  color: var(--brass);
}

.num {
  padding: 1px 6px;
  border: 1px solid var(--brass-2);
  border-radius: 999px;
  font-size: 10px;
  letter-spacing: 0.06em;
}
</style>
