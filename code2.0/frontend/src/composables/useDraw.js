import { ref } from 'vue'
import * as api from '../api/index.js'

/**
 * 打捞的仪式。
 *
 * 它住在全站层，不能住在首页：跳转会把首页组件卸载掉，仪式跟着一起消失，
 * 人还没看清东西被提上来，画面已经换了。
 *
 * 一次打捞 = 垂下（幕布淡入、垂线落下）
 *          → 勾住（光点亮起、涟漪散开）
 *          → 提起（保持住，让人看见这一下）
 *          → 摊开（幕布抬起时，新展品已经在底下站稳了）
 *
 * 四个节拍都写在同一个时间轴上，跳转藏在第三拍里，
 * 所以路由转场不再是一刀切断，而是这场戏的一部分。
 */

const RITUAL_MS = 2400 // 垂下 → 勾住 → 提起。和 DrawVeil 里的 CSS 时序对齐
const NAVIGATE_AT_MS = 1700 // 这时换页：路由的 out-in 转场（约 600ms）藏在仪式后半段
const SETTLE_MS = 320 // 幕布抬起前的余量：新页面渲染完了再亮出来
const ERROR_MS = 1600 // 捞不上来时，让人看清那句话

// 系统里开了「减少动态效果」的人，看到的是静止的幕布 ——
// 那就不该按着他们看 2.4 秒黑屏，仪式留一个短促的交代就够。
const REDUCED_RITUAL_MS = 260
const REDUCED_SETTLE_MS = 60

const drawing = ref(false)
const error = ref('')
/** 每次打捞 +1。捞到同一件东西时参数没变，页面靠它重演一次亮相。 */
const seq = ref(0)
/** 这一轮捞到的是谁。用来区分「捞到的是你」和「捞到的是别人还在路上」。 */
const last = ref(null)

let router = null
let busy = false

/** App.vue 在 setup 里调一次，把路由交进来。 */
export function attachDraw(instance) {
  router = instance
}

export function useDraw() {
  return { drawing, error, seq, last, draw }
}

async function draw() {
  if (busy || !router) return
  busy = true
  error.value = ''
  drawing.value = true
  document.documentElement.classList.add('is-drawing')

  const startedAt = Date.now()
  const ritualMs = reduceMotion() ? REDUCED_RITUAL_MS : RITUAL_MS
  const settleMs = reduceMotion() ? REDUCED_SETTLE_MS : SETTLE_MS

  try {
    // 幕布后面不该有两条声轨：上一段先停下，新的那一段才起得来
    document.querySelectorAll('audio').forEach((el) => el.pause())

    const result = await api.draw()
    await holdTo(reduceMotion() ? REDUCED_RITUAL_MS : NAVIGATE_AT_MS, startedAt)

    const target =
      result.type === 'heritage'
        ? { name: 'archive', params: { id: result.heritage.slug } }
        : { name: 'exhibit', params: { id: result.exhibit.no } }

    last.value = { kind: result.type, key: target.params.id }
    seq.value += 1
    // from=draw 是「这一件是捞上来的」的唯一凭据：
    // 从馆藏点进来时没有它，页面就不会假装你是捞到的。
    await router.push({ ...target, query: { from: 'draw' } })

    await holdTo(ritualMs, startedAt)
    await sleep(settleMs)
  } catch (e) {
    error.value = e?.message || '没能捞上来'
    await sleep(ERROR_MS)
  } finally {
    drawing.value = false
    busy = false
    document.documentElement.classList.remove('is-drawing')
  }
}

/** 把时间轴撑到第 ms 毫秒 —— 请求回来得快，戏也要演满。 */
function holdTo(ms, startedAt) {
  return sleep(Math.max(0, ms - (Date.now() - startedAt)))
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const reduceMotion = () =>
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches
