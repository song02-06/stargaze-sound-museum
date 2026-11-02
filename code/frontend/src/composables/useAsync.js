import { ref, onMounted } from 'vue'

/**
 * 一屏数据的最小状态机：加载中 / 出错 / 拿到了。
 *
 * 接了后端之后这三态是必须的 —— 网络会慢，会失败，
 * 不能假装数据永远立刻就到。
 */
export function useAsync(loader, { immediate = true } = {}) {
  const data = ref(null)
  // 首次渲染必须已经是「加载中」：否则 mounted 之前 data 还是 null，
  // 模板里的 items.length 会在这一刻炸掉。
  const loading = ref(immediate)
  const error = ref('')

  async function run() {
    loading.value = true
    error.value = ''
    try {
      data.value = await loader()
    } catch (e) {
      error.value = e?.message || '加载失败'
    } finally {
      loading.value = false
    }
  }

  if (immediate) {
    onMounted(run)
  }

  return { data, loading, error, run }
}
