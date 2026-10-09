import { ref, computed, onUnmounted, watch } from 'vue'

/**
 * 极简播放器。产品原则是「声音是主角」，所以这里不做队列、不做倍速、
 * 不上波形交互 —— 只有播放、暂停、进度。
 */
export function usePlayer() {
  const el = ref(null)
  const playing = ref(false)
  const current = ref(0)
  const duration = ref(0)
  const blocked = ref(false)
  const failed = ref(false)

  let audio = null

  function onLoadedMetadata() {
    duration.value = Number.isFinite(audio.duration) ? audio.duration : 0
  }

  function onTimeUpdate() {
    current.value = audio.currentTime
  }

  function onPlay() {
    playing.value = true
    blocked.value = false
  }

  function onPause() {
    playing.value = false
  }

  function onEnded() {
    playing.value = false
    current.value = 0
  }

  function onError() {
    failed.value = true
    playing.value = false
  }

  function detach() {
    if (!audio) return
    audio.removeEventListener('loadedmetadata', onLoadedMetadata)
    audio.removeEventListener('timeupdate', onTimeUpdate)
    audio.removeEventListener('play', onPlay)
    audio.removeEventListener('pause', onPause)
    audio.removeEventListener('ended', onEnded)
    audio.removeEventListener('error', onError)
  }

  /**
   * `<audio :ref="bind">` 是函数 ref，而 Vue 在**每次重新渲染**时都会
   * 把同一个元素再交给它一次（见 runtime-core 的 patch()）。
   *
   * 早先这里每次都 `pause()` + 重挂一遍监听，于是：
   * play() → play 事件 → playing 变化 → 重渲染 → 立刻 pause，
   * 声音只响一帧。所以同一个节点只绑一次，其余情况原样返回。
   */
  function bind(node) {
    if (node === audio) return
    if (audio) {
      audio.pause()
      detach()
      audio = null
    }
    el.value = node
    if (!node) return
    audio = node
    node.addEventListener('loadedmetadata', onLoadedMetadata)
    node.addEventListener('timeupdate', onTimeUpdate)
    node.addEventListener('play', onPlay)
    node.addEventListener('pause', onPause)
    node.addEventListener('ended', onEnded)
    node.addEventListener('error', onError)
  }

  /** 换了一条声音（比如又捞到一件）时清干净上一件留下的状态 */
  function reset() {
    playing.value = false
    current.value = 0
    duration.value = 0
    blocked.value = false
    failed.value = false
  }

  async function play() {
    if (!audio) return
    try {
      await audio.play()
    } catch {
      // 浏览器拦下了自动播放：把控制权交回给用户
      blocked.value = true
      playing.value = false
    }
  }

  function toggle() {
    if (!audio) return
    if (audio.paused) play()
    else audio.pause()
  }

  function seekTo(ratio) {
    if (!audio || !duration.value) return
    audio.currentTime = Math.max(0, Math.min(1, ratio)) * duration.value
  }

  const progress = computed(() =>
    duration.value ? Math.min(1, current.value / duration.value) : 0
  )

  onUnmounted(() => {
    if (audio) {
      audio.pause()
      audio = null
    }
  })

  return {
    el,
    bind,
    reset,
    playing,
    current,
    duration,
    progress,
    blocked,
    failed,
    play,
    toggle,
    seekTo
  }
}
