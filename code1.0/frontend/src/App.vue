<template>
  <!-- :key 让切换画质时真正重建场景 —— 否则 WebGL 资源只在挂载时初始化一次，
       「画质」按钮会变成一个没有反应的开关 -->
  <StarField :key="lowSpec ? 'low' : 'full'" ref="starField" :low-spec="lowSpec" />

  <div class="app">
    <AppHeader
      v-model="view"
      :online="online"
      :total="stats.pass"
      :low-spec="lowSpec"
      :status-title="statusTitle"
      @toggle-low-spec="lowSpec = !lowSpec"
    />

    <main class="stage">
      <Transition name="fade" mode="out-in">
        <!-- ── 捞一段 ── -->
        <section v-if="view === 'draw'" key="draw" class="view">
          <!-- 打捞中：先给一点悬念，再揭晓 -->
          <div v-if="drawing" class="drawing">
            <div class="ripples">
              <span v-for="i in 3" :key="i" :style="{ animationDelay: `${i * 0.5}s` }"></span>
            </div>
            <p>正在星空里打捞…</p>
          </div>

          <StoryCard
            v-else-if="drawn"
            :item="drawn"
            @close="drawn = null"
            @next="doDraw"
            @level="onLevel"
          />

          <div v-else class="home">
            <!-- 顶部道具条：对应参考图里的「同城卡 / 加速卡 / 定位卡」那一排 -->
            <div class="tool-row">
              <button class="tool-card t-blue" @click="view = 'collection'">
                <span class="tl">馆藏</span>
                <span class="tv">{{ stats.pass }}</span>
              </button>
              <button class="tool-card t-pink" @click="doDraw">
                <span class="tl">历史彩蛋</span>
                <span class="tv">{{ stats.heritage }}</span>
              </button>
              <button class="tool-card t-purple" @click="view = 'record'">
                <span class="tl">复审队列</span>
                <span class="tv">{{ stats.review }}</span>
              </button>
            </div>

            <!-- 星球位：canvas 里的星球正好落进这一块，别放内容 -->
            <div class="planet-slot" aria-hidden="true"></div>

            <div class="cta-block">
              <button class="btn primary lg" @click="doDraw">开始打捞</button>
              <p class="cta-hint">
                星空里沉了 <em>{{ stats.pass }}</em> 段陌生的声音
              </p>
            </div>

            <!-- 两列功能卡：标题 + 副标题 + 胶囊按钮，和参考图一致的层级 -->
            <div class="feature-grid">
              <button
                v-for="f in features"
                :key="f.key"
                class="feature-card"
                :class="f.tone"
                @click="onFeature(f)"
              >
                <span class="fc-title">{{ f.title }}</span>
                <span class="fc-sub">{{ f.sub }}</span>
                <span class="fc-action">{{ f.action }}</span>
              </button>
            </div>

            <div class="section-head">
              <h3>精选馆藏</h3>
              <button class="more" @click="view = 'collection'">全部 ›</button>
            </div>

            <div v-if="collection.length" class="mini-grid">
              <button
                v-for="item in collection.slice(0, 4)"
                :key="item.id"
                class="mini-card"
                @click="openFromCollection(item)"
              >
                <span class="mini-title">{{ item.title || '未命名的一段' }}</span>
                <span class="mini-sub">{{ item.emotion || '陌生人的留言' }}</span>
              </button>
            </div>
            <p v-else class="mini-empty">馆藏还是空的，先去留一段吧。</p>
          </div>
        </section>

        <!-- ── 留一段 ── -->
        <section v-else-if="view === 'record'" key="record" class="view">
          <Recorder @recorded="onRecorded" />
        </section>

        <!-- ── 馆藏 ── -->
        <section v-else key="collection" class="view">
          <CollectionView
            :items="collection"
            :loading="loadingCollection"
            @select="openFromCollection"
          />
        </section>
      </Transition>
    </main>

    <footer class="footer">
      <span>课程作业 · Demo</span>
      <span class="sep">·</span>
      <span>转写 {{ providers.asr }} / 润色 {{ providers.ai }}</span>
      <span class="sep">·</span>
      <span>审核队列 {{ stats.review }} 条</span>
    </footer>
  </div>

  <Transition name="fade">
    <div v-if="toast" class="toast glass">{{ toast }}</div>
  </Transition>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import StarField from './components/StarField.vue'
import AppHeader from './components/AppHeader.vue'
import StoryCard from './components/StoryCard.vue'
import Recorder from './components/Recorder.vue'
import CollectionView from './components/CollectionView.vue'
import { draw, ping, recentBottles, stats as fetchStats, uploadBottle } from './api'

const starField = ref(null)

const view = ref('draw') // draw | record | collection
const online = ref(false)
const lowSpec = ref(false)
const drawing = ref(false)
const drawn = ref(null)
const toast = ref('')

const collection = ref([])
const loadingCollection = ref(false)

const stats = ref({ pass: 0, review: 0, reject: 0, pending: 0, heritage: 0, total: 0 })
const providers = ref({ asr: 'mock', ai: 'mock' })

// 首页功能卡：和参考图一样是「标题 + 一句人话 + 胶囊按钮」的三层结构
const features = [
  { key: 'draw', title: '随机打捞', sub: '和懂你的人聊天', action: '开始打捞', tone: 't-blue' },
  { key: 'heritage', title: '历史彩蛋', sub: '听见一段旧时光', action: '开始打捞', tone: 't-pink' },
  { key: 'record', title: '留一段', sub: '把故事沉进星空', action: '去录音', tone: 't-purple' },
  { key: 'collection', title: '馆藏', sub: '看看别人留下了什么', action: '去逛逛', tone: 't-slate' }
]

let toastTimer = null

const statusTitle = ref('')

function showToast(message, duration = 3600) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), duration)
}

async function refreshStats() {
  try {
    const [s, p] = await Promise.all([fetchStats(), ping()])
    stats.value = s
    providers.value = { asr: p.asrProvider, ai: p.aiProvider }
    online.value = true
    statusTitle.value = `转写 ${p.asrProvider} · 润色 ${p.aiProvider} · 敏感词 ${p.sensitiveWords} 条`
  } catch {
    online.value = false
    statusTitle.value = '后端未启动：请先运行 mvn spring-boot:run（端口 8080）'
  }
}

async function loadCollection() {
  loadingCollection.value = true
  try {
    collection.value = await recentBottles()
  } catch {
    collection.value = []
  } finally {
    loadingCollection.value = false
  }
}

async function doDraw() {
  if (drawing.value) return
  drawing.value = true
  drawn.value = null
  // 打捞开始：星球加速自转、流星加密
  starField.value?.setSearching(true)
  const startedAt = Date.now()
  try {
    const item = await draw()
    // 打捞动画至少停 2400ms：接口通常几十毫秒就回来了，
    // 星球要靠这段时间完成「加速自转 → 流星划过 → 揭晓」的过程，否则刚转起来就结束。
    const remain = Math.max(0, 2400 - (Date.now() - startedAt))
    if (remain) await new Promise((r) => setTimeout(r, remain))
    drawn.value = item
    if (item.type === 'HERITAGE') starField.value?.pulse(1)
  } catch (e) {
    showToast(e.message || '打捞失败，稍后再试')
  } finally {
    drawing.value = false
    starField.value?.setSearching(false)
  }
}

function onFeature(f) {
  if (f.key === 'record') {
    view.value = 'record'
    return
  }
  if (f.key === 'collection') {
    view.value = 'collection'
    return
  }
  doDraw()
}

async function onRecorded({ blob, durationMs, note }) {
  showToast('正在转写与润色，请稍候…', 30000)
  try {
    const bottle = await uploadBottle(blob, note, durationMs)
    toast.value = ''
    drawn.value = { ...bottle, type: 'BOTTLE', story: bottle.polishedText || bottle.rawTranscript }
    view.value = 'draw'
    showToast(describeAudit(bottle), 5000)
    refreshStats()
    loadCollection()
  } catch (e) {
    showToast(e.message || '上传失败，稍后再试')
  }
}

function describeAudit(bottle) {
  switch (bottle.auditStatus) {
    case 'PASS':
      return '审核通过，已经沉入星空'
    case 'REVIEW':
      return `已进入人工复审：${bottle.auditReason || ''}`
    case 'REJECT':
      return `未通过审核：${bottle.auditReason || ''}`
    default:
      return bottle.auditReason || '已保存，等待处理'
  }
}

function openFromCollection(item) {
  drawn.value = { ...item, type: 'BOTTLE', story: item.polishedText || item.rawTranscript }
  view.value = 'draw'
}

/** 播放器把实时音量抛上来，转给星空 —— 这就是「声音驱动视觉」的接线处 */
function onLevel(level) {
  starField.value?.setAudioLevel(level)
}

onMounted(async () => {
  await refreshStats()
  await loadCollection()
})

onBeforeUnmount(() => {
  clearTimeout(toastTimer)
})
</script>

<style scoped>
.app {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  /* 空白处放行给底下的 WebGL canvas，这样拖拽空白可以转动星云视角；
     真正需要交互的元素各自把 pointer-events 打开。 */
  pointer-events: none;
}

.stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: clamp(16px, 3vw, 40px) clamp(16px, 4vw, 44px);
  overflow-y: auto;
  min-height: 0;
}

.view {
  width: 100%;
  display: flex;
  justify-content: center;
  /* 用 auto 外边距做垂直居中：内容再高也不会被裁掉顶部 */
  margin: auto 0;
  pointer-events: none;
}

/* 需要交互的部分单独放行 */
.stage :deep(.card),
.stage :deep(.recorder),
.stage :deep(.collection),
.home {
  pointer-events: auto;
}

/* ---------- 首页：排布参考 Soul 的星球页 ---------- */
.home {
  width: min(100%, 880px);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 顶部道具条：对应参考图的「同城卡 / 加速卡 / 定位卡」 */
.tool-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.tool-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
  border-radius: var(--r-md);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #fff;
  text-align: left;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.tool-card .tl {
  font-size: 12px;
  opacity: 0.88;
  letter-spacing: 0.08em;
}

.tool-card .tv {
  font-size: 18px;
  font-weight: 600;
}

.tool-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.38);
}

/* 星球位：这块留白就是 canvas 里那颗星球落下的位置，别往里放内容 */
.planet-slot {
  height: clamp(200px, 34vh, 340px);
}

.cta-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.cta-hint {
  margin: 0;
  font-size: 13px;
  color: var(--ink-3);
  letter-spacing: 0.06em;
}

.cta-hint em {
  font-style: normal;
  color: var(--accent);
}

/* 两列功能卡 */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.feature-card {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 参考图里卡片接近 1.5:1，比原先的扁条更像内容卡 */
  min-height: 168px;
  padding: 18px;
  border-radius: var(--r-lg);
  border: 1px solid rgba(255, 255, 255, 0.16);
  color: #fff;
  text-align: left;
  overflow: hidden;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

/* 右下角一层柔光：纯渐变会显得平，加一点光才有「卡片」的厚度 */
.feature-card::after {
  content: '';
  position: absolute;
  right: -22%;
  bottom: -48%;
  width: 118%;
  height: 150%;
  z-index: -1;
  background: radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.32), transparent 60%);
}

.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 20px 44px rgba(0, 0, 0, 0.45);
}

.fc-title {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.fc-sub {
  font-size: 12.5px;
  opacity: 0.84;
}

.fc-action {
  margin-top: auto;
  align-self: flex-start;
  padding: 7px 18px;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.92);
  color: #12111f;
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

/* 卡片配色是照参考图逐点取样来的：
   蓝卡实测 #728dbc → #5da4e2 → #1fc4fe，品红卡 #b451ab，紫卡 #926eaf，派对卡底 #253545 */
.t-blue {
  background: linear-gradient(135deg, #5b8ec8 0%, #4f8fdc 42%, #1fc4fe 100%);
}

.t-pink {
  background: linear-gradient(135deg, #e07ac2 0%, #b451ab 55%, #7a3a9a 100%);
}

.t-purple {
  background: linear-gradient(135deg, #b39ae8 0%, #926eaf 55%, #4d3f86 100%);
}

.t-slate {
  background: linear-gradient(135deg, #35526d 0%, #253545 58%, #141d27 100%);
}

/* 分区标题 */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 2px;
}

.section-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.section-head .more {
  font-size: 12.5px;
  color: var(--ink-3);
}

.section-head .more:hover {
  color: var(--accent);
}

.mini-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mini-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 84px;
  padding: 16px;
  border-radius: var(--r-md);
  border: 1px solid rgba(159, 216, 255, 0.14);
  background: rgba(37, 53, 69, 0.55);
  text-align: left;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.mini-card:hover {
  transform: translateY(-2px);
  border-color: rgba(159, 216, 255, 0.4);
}

.mini-title {
  overflow: hidden;
  font-size: 14px;
  color: var(--ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-sub {
  font-size: 12px;
  color: var(--ink-3);
}

.mini-empty {
  margin: 0;
  font-size: 12.5px;
  color: var(--ink-3);
}

/* ---------- 打捞中 ---------- */
.drawing {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
  color: var(--ink-3);
  letter-spacing: 0.14em;
  font-size: 13px;
}

.ripples {
  position: relative;
  width: 260px;
  height: 260px;
  display: grid;
  place-items: center;
}

.ripples span {
  position: absolute;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: 1px solid rgba(159, 216, 255, 0.7);
  animation: ripple 1.8s cubic-bezier(0.2, 0.6, 0.3, 1) infinite;
}

@keyframes ripple {
  0% {
    width: 40px;
    height: 40px;
    opacity: 0.85;
  }
  100% {
    width: 240px;
    height: 240px;
    opacity: 0;
  }
}

/* ---------- 页脚 ---------- */
.footer {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px clamp(16px, 4vw, 44px) 18px;
  font-size: 11.5px;
  color: var(--ink-3);
  letter-spacing: 0.04em;
  pointer-events: auto;
}

.footer .sep {
  opacity: 0.5;
}

.toast {
  position: fixed;
  z-index: 40;
  left: 50%;
  bottom: 74px;
  transform: translateX(-50%);
  padding: 13px 24px;
  font-size: 13px;
  max-width: 80vw;
  text-align: center;
  border-radius: var(--r-pill);
  pointer-events: none;
}

@media (max-width: 720px) {
  .home {
    gap: 14px;
  }

  .planet-slot {
    height: clamp(150px, 24vh, 225px);
  }

  .tool-card {
    padding: 10px 12px;
  }

  .tool-card .tv {
    font-size: 16px;
  }

  .feature-card {
    min-height: 118px;
    padding: 14px;
  }

  .fc-title {
    font-size: 16.5px;
  }

  .footer {
    flex-wrap: wrap;
    gap: 4px 8px;
  }
}
</style>
