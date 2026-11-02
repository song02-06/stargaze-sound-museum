<template>
  <div class="home">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/" exact-active-class="is-current">星空</RouterLink>
        <RouterLink to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
      </template>
    </AppTopBar>

    <section class="hero rail" :class="{ drawing }" aria-labelledby="home-title">
      <div class="hero-copy">
        <h1 id="home-title" class="brush-title" aria-label="循着星轨 听见人间"><span>循着星轨</span><span>听见人间</span></h1>
        <p class="intro"><span>把一段日常留在这里</span><span>也从陌生人的世界里，打捞一束回响</span></p>
        <div class="holdings" aria-live="polite">
          <p v-if="loading" class="count-message">正在数星星…</p>
          <p v-else-if="error" class="count-message">星星数不清了</p>
          <p v-else-if="total === 0" class="count-message">星空暂时安静了</p>
          <p v-else class="count">星空里沉了<strong>{{ total }}</strong>段声音</p>
          <p v-if="!loading && !error" class="heritage">
            <template v-if="total === 0">埋下一段声音，让星空再次响起。</template>
            <template v-else-if="heritageCount">其中<span class="inline-number">{{ heritageCount }}</span>段，来自历史深处</template>
            <template v-else>每一段声音，都有它的来处。</template>
          </p>
        </div>
        <StateNote v-if="error" error :on-retry="run">{{ error }}</StateNote>
        <div class="actions">
          <button class="btn primary salvage" type="button" :disabled="loading || !!error || drawing || total === 0" :aria-busy="drawing" @click="draw()">
            <AppIcon :name="drawing ? 'loading' : 'draw'" :class="{ spinner: drawing }" :size="18" />
            {{ drawing ? '正在打捞…' : '打捞一段声音' }}
          </button>
          <RouterLink class="bury" to="/bury"><AppIcon name="bury" :size="17" />埋下一段</RouterLink>
        </div>
      </div>

      <figure ref="orbitArea" class="sound-map" aria-label="最近入馆的声音星图" @pointermove="moveMap" @pointerleave="resetMap">
        <div class="map-scene">
        <div class="map-glow" aria-hidden="true" />
        <svg class="orbits" viewBox="0 0 640 520" fill="none" aria-hidden="true">
          <defs>
            <radialGradient id="home-star-glow"><stop stop-color="#f3e0b0" stop-opacity=".8"/><stop offset=".12" stop-color="#d1b680" stop-opacity=".22"/><stop offset="1" stop-color="#c9a96b" stop-opacity="0"/></radialGradient>
            <linearGradient id="home-orbit" x1="70" y1="100" x2="560" y2="430" gradientUnits="userSpaceOnUse"><stop stop-color="#c9a96b" stop-opacity=".08"/><stop offset=".5" stop-color="#e0c58f" stop-opacity=".65"/><stop offset="1" stop-color="#c9a96b" stop-opacity=".12"/></linearGradient>
          </defs>
          <circle cx="320" cy="258" r="211" stroke="#a6bad8" stroke-opacity=".1"/>
          <circle cx="320" cy="258" r="171" stroke="#a6bad8" stroke-opacity=".08" stroke-dasharray="2 8"/>
          <circle cx="320" cy="258" r="123" stroke="#a6bad8" stroke-opacity=".08"/>
          <path ref="outerOrbit" :d="orbitPath(0, 0, 0)" stroke="url(#home-orbit)"/>
          <path ref="thirdOrbit" :d="orbitPath(2, 0, 0)" stroke="#c4c9d4" stroke-opacity=".16"/>
          <path ref="innerOrbit" :d="orbitPath(1, 0, 0)" stroke="#9badc8" stroke-opacity=".24"/>
          <path d="M320 30V59M320 456V485M90 258H119M521 258H550" stroke="#b0bacb" stroke-opacity=".25"/>
          <circle class="star-breath" cx="320" cy="258" r="86" fill="url(#home-star-glow)"/>
          <path class="anchor-star" d="M320 239L323 255L339 258L323 261L320 277L317 261L301 258L317 255Z" fill="#ecd7ac"/>
          <circle cx="320" cy="258" r="26" stroke="#ddc18c" stroke-opacity=".25"/>
          <circle cx="91" cy="304" r="2" fill="#c9a96b"/><circle cx="534" cy="176" r="2" fill="#c9a96b"/>
          <circle cx="241" cy="63" r="1.5" fill="#a8bad4"/><circle cx="513" cy="365" r="1.5" fill="#a8bad4"/>
          <circle cx="193" cy="421" r="2" fill="#a8bad4"/>
        </svg>
        <span class="map-wordmark" aria-hidden="true">声音在此交汇</span>
        <template v-if="!recentError">
          <RouterLink v-for="(item, index) in recentItems || []" :key="item.id" :to="`/exhibit/${item.no}`" class="sound-star" :class="`star-${index}`" :aria-label="`聆听：${item.title}`">
            <svg class="star-dot" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 1L14.5 9.5L23 12L14.5 14.5L12 23L9.5 14.5L1 12L9.5 9.5Z" fill="currentColor"/></svg>
            <span class="star-number">{{ item.no }}</span>
          </RouterLink>
        </template>
        </div>
        <figcaption><span class="map-key" aria-hidden="true" />{{ recentItems?.length ? '点击星点，走近一段声音' : '等待下一段声音，点亮星空' }}</figcaption>
      </figure>
    </section>

    <section class="recent rail" aria-labelledby="recent-title">
      <div class="section-head">
        <div class="section-title"><h2 id="recent-title" class="serif">最近入馆</h2><p>平凡的一刻，也值得被收藏。</p></div>
        <RouterLink class="all-sounds" to="/collection">漫游全部馆藏<AppIcon name="chevron-right" :size="15" /></RouterLink>
      </div>
      <StateNote v-if="recentLoading">正在寻找新入馆的声音…</StateNote>
      <StateNote v-else-if="recentError" error :on-retry="recentRun">{{ recentError }}</StateNote>
      <p v-else-if="!recentItems?.length" class="empty-recent">还没有声音入馆。第一段声音，或许就来自你。</p>
      <ul v-else class="recent-grid">
        <li v-for="item in recentItems" :key="item.id">
          <RouterLink class="sound-card" :to="`/exhibit/${item.no}`">
            <div class="card-top"><span class="catalog-number">NO. {{ item.no }}</span><span class="duration"><AppIcon name="headphones" :size="13" />{{ clock(item.seconds) }}</span></div>
            <h3 class="serif">{{ item.title }}</h3>
            <WaveTrace v-if="item.trace?.length" class="card-wave" :trace="item.trace" mini :label="`${item.title}的声音波形`" />
            <div v-else class="wave-placeholder" aria-hidden="true" />
            <div class="card-bottom"><span class="place">{{ item.place || '未注明地点' }}</span><span class="when">{{ sinceLabel(daysAgo(item.date)) }}</span><span class="listen-cue"><AppIcon name="play" :size="14" /></span></div>
          </RouterLink>
        </li>
      </ul>
    </section>
    <footer class="home-footer rail"><span>星轨 · 声音博物馆</span><span>收藏声音，也收藏声音背后的生活。</span><RouterLink to="/archive">去听历史的回响<AppIcon name="chevron-right" :size="13" /></RouterLink></footer>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import AppIcon from '../components/AppIcon.vue'
import StateNote from '../components/StateNote.vue'
import WaveTrace from '../components/WaveTrace.vue'
import { useAsync } from '../composables/useAsync.js'
import { useDraw } from '../composables/useDraw.js'
import * as api from '../api/index.js'
import { daysAgo, sinceLabel, clock } from '../lib/format.js'
const { data: stats, loading, error, run } = useAsync(() => api.ping())
const { data: recentItems, loading: recentLoading, error: recentError, run: recentRun } = useAsync(() => api.recent(3))
const { drawing, draw } = useDraw()
const total = computed(() => stats.value ? stats.value.exhibitCount + stats.value.heritageCount : 0)
const heritageCount = computed(() => stats.value?.heritageCount || 0)

// 轨道缓慢旋转；声音入口在独立的位置轻轻漂浮。
const orbitArea = ref(null)
const outerOrbit = ref(null)
const innerOrbit = ref(null)
const thirdOrbit = ref(null)
let motionPreference
let pointerFrame = 0
let lastTime = 0
let orbitTime = 0
let inView = true
let orbitObserver
let targetX = 0, targetY = 0, currentX = 0, currentY = 0
const orbitLayers = [
  { rx: 269, ry: 100, angle: -30, depth: 1, period: 150 },
  { rx: 244, ry: 78, angle: 38, depth: .62, period: -180 },
  { rx: 232, ry: 85, angle: 68, depth: .72, period: 200 }
]
function orbitPoint(layer, t, x, y) {
  const { rx, ry, angle, depth, period } = orbitLayers[layer]
  const tilt = angle * Math.PI / 180
  const turn = orbitTime * Math.PI * 2 / period
  const px = rx * Math.cos(t), py = ry * Math.sin(t)
  const baseX = px * Math.cos(tilt) - py * Math.sin(tilt)
  const baseY = px * Math.sin(tilt) + py * Math.cos(tilt)
  const pull = depth * (.45 + .55 * (px * px + py * py) / (rx * rx + ry * ry))
  return {
    x: 320 + baseX * Math.cos(turn) - baseY * Math.sin(turn) + x * 65 * pull + y * Math.sin(2 * t) * 12 * depth,
    y: 258 + baseX * Math.sin(turn) + baseY * Math.cos(turn) + y * 48 * pull + x * Math.cos(2 * t) * 8 * depth
  }
}
function orbitPath(layer, x, y) {
  return Array.from({ length: 129 }, (_, i) => {
    const p = orbitPoint(layer, i / 128 * Math.PI * 2, x, y)
    return `${i ? 'L' : 'M'}${p.x.toFixed(3)},${p.y.toFixed(3)}`
  }).join(' ') + ' Z'
}
function paintOrbits() {
  outerOrbit.value?.setAttribute('d', orbitPath(0, currentX, currentY))
  innerOrbit.value?.setAttribute('d', orbitPath(1, currentX, currentY))
  thirdOrbit.value?.setAttribute('d', orbitPath(2, currentX, currentY))
}
function animateOrbits(time) {
  const dt = lastTime ? Math.min((time - lastTime) / 1000, .05) : 1 / 60
  lastTime = time
  orbitTime += dt
  const blend = 1 - Math.exp(-7 * dt)
  currentX += (targetX - currentX) * blend
  currentY += (targetY - currentY) * blend
  const settled = Math.abs(targetX - currentX) + Math.abs(targetY - currentY) < .0005
  if (settled) { currentX = targetX; currentY = targetY }
  paintOrbits()
  pointerFrame = requestAnimationFrame(animateOrbits)
}
function startMotion() {
  if (!pointerFrame && !document.hidden && !motionPreference?.matches && inView) pointerFrame = requestAnimationFrame(animateOrbits)
}
function moveMap(event) {
  if (event.pointerType !== 'mouse' || motionPreference?.matches || drawing.value) return
  const box = orbitArea.value.getBoundingClientRect()
  targetX = Math.max(-1, Math.min(1, (event.clientX - box.left) / box.width * 2 - 1))
  targetY = Math.max(-1, Math.min(1, (event.clientY - box.top) / box.height * 2 - 1))
  startMotion()
}
function resetMap() {
  targetX = targetY = 0
  if (document.hidden || motionPreference?.matches || !inView) {
    cancelAnimationFrame(pointerFrame)
    pointerFrame = lastTime = currentX = currentY = 0
    paintOrbits()
  } else startMotion()
}
onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  motionPreference.addEventListener('change', resetMap)
  document.addEventListener('visibilitychange', resetMap)
  orbitObserver = new IntersectionObserver(([entry]) => {
    inView = entry.isIntersecting
    resetMap()
  })
  orbitObserver.observe(orbitArea.value)
  startMotion()
})
onUnmounted(() => {
  orbitObserver?.disconnect()
  cancelAnimationFrame(pointerFrame)
  motionPreference?.removeEventListener('change', resetMap)
  document.removeEventListener('visibilitychange', resetMap)
})
</script>

<style scoped>
@font-face {
  font-family: 'Museum Running Brush';
  src: url('/fonts/zhi-mang-xing-title.ttf') format('truetype');
  font-style: normal;
  font-weight: 400;
  font-display: swap;
}

.home {
  --home-warm: #e5c895;
  --home-soft: #a4afc2;
  --home-rule: rgb(193 209 233 / .13);
  width: 100%; max-width: var(--content-max); min-height: 100dvh; margin-inline: auto;
}
.home :deep(.topbar) { height: 90px; }
.home :deep(.brand) { gap: 10px; }
.home :deep(.brand b) { font-size: 20px; color: var(--home-warm); letter-spacing: .12em; }
.home :deep(.brand span) { color: var(--home-soft); }
.hero { display: grid; grid-template-columns: 46% 54%; align-items: center; position: relative; min-height: calc(100dvh - 91px); padding-top: 40px; padding-bottom: 100px; }
.hero-copy { position: relative; z-index: 2; transform: translateY(42px); }
h1 { color: #f0ede5; font-family: 'Museum Running Brush', 'STXingkai', 'KaiTi', cursive; font-size: 78px; font-weight: 400; letter-spacing: .07em; line-height: 1.15; transform: translateY(-28px); }
h1 span { display: block; width: fit-content; white-space: nowrap; }
h1 span + span { margin-top: 20px; margin-left: 88px; color: var(--home-warm); }
.intro { margin-top: 38px; font-size: 15px; line-height: 1.95; letter-spacing: .06em; color: var(--home-soft); }
.intro span { display: block; }
.intro span + span { margin-top: 10px; margin-left: 32px; }
.holdings { margin-top: 36px; margin-left: 88px; min-height: 91px; }
.count { display: flex; align-items: baseline; color: #c6cfdd; font-size: 17px; letter-spacing: .08em; line-height: 1.1; }
.count strong { margin-inline: 12px; font-family: Georgia, var(--mono); font-weight: 400; color: #f0ede5; font-size: 48px; letter-spacing: -.02em; font-variant-numeric: lining-nums tabular-nums; }
.count-message { padding-top: 10px; color: var(--ink); font-size: 22px; }
.heritage { color: var(--home-soft); font-size: 13px; letter-spacing: .08em; margin-top: 12px; }
.inline-number { margin-inline: .25em; font-variant-numeric: tabular-nums; }
.actions { display: flex; align-items: center; gap: 30px; margin-top: 28px; margin-left: 32px; }
.actions .salvage { height: 52px; border: 1px solid #e5c895; border-radius: 5px; background: #e5c895; color: #252016; font-size: 14px; letter-spacing: .12em; text-indent: 0; padding-inline: 26px; transition: transform var(--dur-fast) var(--ease), background var(--dur-fast), box-shadow var(--dur-fast); }
.actions .salvage:hover:not(:disabled) { transform: translateY(-3px); background: #f3dab0; box-shadow: 0 8px 28px rgb(201 169 107 / .15); }
.actions .salvage:disabled { pointer-events: none; }
.actions .salvage[aria-busy="true"] { opacity: 1; }
.bury { display: inline-flex; align-items: center; gap: 8px; padding-block: 14px; color: #c6cfdd; font-size: 14px; letter-spacing: .1em; transition: transform var(--dur-fast); }
.bury:hover { transform: translateY(2px); color: var(--home-warm); text-decoration: none; }

.sound-map { position: relative; aspect-ratio: 640 / 520; width: 100%; max-width: 650px; justify-self: end; isolation: isolate; }
.map-scene { position: absolute; inset: 0; }
.map-glow { position: absolute; inset: 9%; z-index: -1; background: radial-gradient(ellipse, rgb(100 113 149 / .09), transparent 68%); }
.orbits { display: block; width: 100%; height: 100%; overflow: visible; }
.star-breath { transform-origin: 320px 258px; animation: star-breathe 9s ease-in-out infinite; }
.drawing .star-breath { animation: star-ripple 1.1s ease-out forwards; }
.map-wordmark { position: absolute; top: 57%; left: 50%; transform: translateX(-50%); font-size: 11px; letter-spacing: .28em; color: var(--home-soft); white-space: nowrap; }
.sound-star { position: absolute; display: flex; align-items: center; min-width: 44px; min-height: 44px; gap: 5px; z-index: 2; text-decoration: none; transform: translate(-8px, -22px); animation: sound-float 9s ease-in-out infinite; }
.star-0 { left: 23%; top: 31%; animation-duration: 9s; animation-delay: -2s; }
.star-1 { left: 75%; top: 25%; animation-duration: 11s; animation-delay: -6s; }
.star-2 { left: 68%; top: 74%; animation-duration: 10s; animation-delay: -4s; }
.sound-star:hover, .sound-star:focus-within { animation-play-state: paused; }
.star-dot { display: block; flex-shrink: 0; width: 16px; height: 16px; color: var(--home-warm); filter: drop-shadow(0 0 6px rgb(229 200 149 / .45)); transition: transform var(--dur-fast); }
.star-number { color: var(--home-soft); font-family: var(--mono); font-size: 8px; letter-spacing: .04em; font-variant-numeric: tabular-nums; }
.sound-star:hover .star-dot, .sound-star:focus-visible .star-dot { transform: scale(1.3); }
.sound-star:hover .star-number, .sound-star:focus-visible .star-number { color: var(--home-warm); }
figcaption { position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); display: flex; align-items: center; gap: 9px; white-space: nowrap; color: var(--home-soft); font-size: 12px; letter-spacing: .08em; }
.map-key { width: 5px; height: 5px; background: var(--home-warm); border-radius: 50%; }
.recent { position: relative; padding-top: 25px; padding-bottom: 32px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.section-title { display: flex; align-items: baseline; gap: 22px; }
h2 { font-size: 23px; font-weight: 400; letter-spacing: .12em; color: #e7e8e6; }
.section-title p { font-size: 13px; letter-spacing: .06em; color: var(--home-soft); }
.all-sounds { display: inline-flex; align-items: center; gap: 12px; padding: 8px 0; font-size: 13px; color: var(--home-warm); letter-spacing: .06em; }
.recent-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; list-style: none; }
.sound-card { display: block; height: 100%; padding: 20px 23px 16px; border: 1px solid var(--home-rule); border-radius: 8px; background: linear-gradient(120deg, rgb(255 255 255 / .035), rgb(255 255 255 / .012)); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); transition: transform var(--dur-fast) var(--ease), border-color var(--dur-fast), background var(--dur-fast); }
.sound-card:hover, .sound-card:focus-visible { transform: translateY(-5px); border-color: rgb(229 200 149 / .5); background: rgb(229 200 149 / .045); text-decoration: none; }
.card-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--home-soft); font-size: 11px; }
.catalog-number { font-family: var(--mono); letter-spacing: .08em; }
.duration { display: flex; align-items: center; gap: 7px; font-family: var(--mono); font-variant-numeric: tabular-nums; }
h3 { margin-top: 12px; font-size: 19px; font-weight: 400; letter-spacing: .05em; color: #e7e8e6; }
.card-wave { height: 30px; width: 100%; margin-top: 10px; }
.card-wave :deep(.dim) { stroke: rgb(229 200 149 / .5); }
.wave-placeholder { height: 30px; margin-top: 10px; }
.card-bottom { display: flex; align-items: center; gap: 12px; margin-top: 7px; color: var(--home-soft); font-size: 12px; }
.place { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.when { margin-left: auto; white-space: nowrap; }
.listen-cue { display: grid; place-items: center; width: 26px; height: 26px; flex-shrink: 0; color: var(--home-warm); border: 1px solid rgb(229 200 149 / .35); border-radius: 50%; }
.empty-recent { padding: 32px 0; color: var(--home-soft); font-size: 14px; border-top: 1px solid var(--home-rule); }
.home-footer { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 64px; color: var(--home-soft); font-size: 11px; letter-spacing: .08em; }
.home-footer a { display: inline-flex; align-items: center; gap: 9px; }
.spinner { animation: spin 1s linear infinite; }
@keyframes sound-float {
  0%, 100% { transform: translate(-8px, -25px); }
  50% { transform: translate(-6px, -19px); }
}
@keyframes star-breathe { 0%, 100% { opacity: .65; transform: scale(.92); } 50% { opacity: 1; transform: scale(1.08); } }
@keyframes star-ripple { from { opacity: 1; transform: scale(1); } to { opacity: 0; transform: scale(2.5); } }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1180px) {
  .hero { grid-template-columns: 45% 55%; }
  h1 { font-size: 66px; }
  h1 span + span { margin-left: 64px; }
  .holdings { margin-left: 64px; }
  .actions { gap: 20px; }
  .actions .salvage { padding-inline: 18px; }
}
@media (max-height: 800px) {
  .hero { padding-top: 28px; padding-bottom: 80px; }
  .hero-copy { transform: translateY(24px); }
  h1 { font-size: 68px; }
  h1 span + span { margin-top: 16px; }
  .intro { margin-top: 28px; }
  .holdings { margin-top: 28px; }
  .actions { margin-top: 24px; }
}
@media (prefers-reduced-motion: reduce) {
  .map-scene, .orbits { transform: none; transition: none; }
  .star-breath, .drawing .star-breath, .spinner, .sound-star { animation: none; }
  .actions .salvage, .bury, .sound-star .star-dot, .sound-card { transition: none; }
  .actions .salvage:hover, .bury:hover, .sound-card:hover, .sound-card:focus-visible { transform: none; }
}
</style>
