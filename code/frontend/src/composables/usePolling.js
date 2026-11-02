import { onUnmounted, onActivated, ref } from 'vue'

/**
 * 定时取数。
 *
 * 三条规则都是刻意的，缺一条就会变成「看起来在自动更新，实际上在堆请求」：
 *
 * 1）**上一次请求完成之后才排下一次**（不是 setInterval 盲打）。
 *    服务慢了，请求不会叠加成一串超时；
 * 2）**卸载时清掉定时器**，页面走了就不该再发请求；
 * 3）**标签页重新可见时补一次**。后台标签的定时器会被浏览器节流，
 *    切回来先补一次，比等下一个 5 秒更接近「我刚看了一眼」。
 *
 * 失败不改已有数据：网络断了就保留屏幕上已经显示的内容，只把错误浮出来。
 */
export function usePolling(load, { interval = 5000, enabled = true } = {}) {
  const polling = ref(false)
  const lastError = ref('')
  let timer = 0
  let stopped = true
  let inFlight = false

  async function tick() {
    if (stopped || inFlight) return
    inFlight = true
    polling.value = true
    try {
      await load()
      lastError.value = ''
    } catch (e) {
      // 断网时保留已显示内容，只提示这一次没刷新成功
      lastError.value = e?.message || '刷新失败'
    } finally {
      inFlight = false
      polling.value = false
      schedule()
    }
  }

  function schedule() {
    if (stopped) return
    stopTimer()
    timer = window.setTimeout(tick, interval)
  }

  function stopTimer() {
    if (timer) {
      window.clearTimeout(timer)
      timer = 0
    }
  }

  function onVisible() {
    if (document.visibilityState === 'visible') tick()
  }

  function start() {
    if (!enabled) return
    if (!stopped) {
      // 已经在跑：只补一次，不重复挂监听
      tick()
      return
    }
    stopped = false
    document.addEventListener('visibilitychange', onVisible)
    schedule()
  }

  function stop() {
    stopped = true
    stopTimer()
    document.removeEventListener('visibilitychange', onVisible)
  }

  /** 立刻刷新一次（比如自己刚提交完），不打断轮询节奏 */
  function refreshNow() {
    return tick()
  }

  onUnmounted(stop)
  onActivated(() => {
    if (!stopped) tick()
  })

  return { polling, lastError, start, stop, refreshNow }
}
