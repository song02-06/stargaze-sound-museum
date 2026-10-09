import { reactive, computed } from 'vue'
import * as api from '../api/index.js'

/**
 * 全站只在这里持有「我是谁」。
 *
 * 打捞、回音、审核状态全部由后端裁决 —— 彩蛋保底和「本轮不重复」
 * 依赖历史记录，放在客户端等于把规则交给了请求方。
 */
export const state = reactive({
  session: null,
  error: ''
})

let booting = null

export function bootstrap() {
  if (state.session) {
    return Promise.resolve(state.session)
  }
  if (!booting) {
    booting = api
      .ensureSession()
      .then((session) => {
        state.session = session
        state.error = ''
        return session
      })
      .catch((err) => {
        state.error = err?.message || '连不上服务'
        booting = null
        throw err
      })
  }
  return booting
}

export const nickname = computed(() => state.session?.nickname || '访客')

export async function setNickname(name) {
  const clean = String(name || '').trim().slice(0, 12)
  if (!clean) return
  await bootstrap()
  state.session = await api.rename(clean)
}
