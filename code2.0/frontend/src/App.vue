<template>
  <div class="shell" :class="{ 'is-archive': isArchive, 'is-admin': isAdmin }">
    <div class="sky" aria-hidden="true">
      <div class="nebula cool" />
      <div class="nebula warm" />
      <StarField class="stars" :count="isArchive ? 170 : 240" :tint="isArchive ? 'warm' : 'cool'" />
      <div class="vignette" />
    </div>

    <!-- 幕布盖着的时候，底下的东西不该能点、能 Tab 进去 -->
    <main class="stage" :inert="drawing || null">
      <div v-if="crashed" class="crashed rail">
        <p class="eyebrow">出错了</p>
        <h1 class="serif">这一页没能打开</h1>
        <p class="lede">其他页面还能用。刷新一次通常就好了。</p>
        <button class="btn primary" type="button" @click="reload">重新加载</button>
      </div>

      <!-- 错误边界：一个组件渲染失败不该让整页变成空白 -->
      <RouterView v-else v-slot="{ Component }">
        <Transition name="view" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>

    <!-- 打捞的幕布挂在全站层：换页不会把它一起卸掉 -->
    <DrawVeil />
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onErrorCaptured } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StarField from './components/StarField.vue'
import DrawVeil from './components/DrawVeil.vue'
import { bootstrap } from './store/museum.js'
import { attachDraw, useDraw } from './composables/useDraw.js'

const route = useRoute()
const router = useRouter()

attachDraw(router)
const { drawing } = useDraw()

/** 历史之声是另一个世界：深空退掉，换成纸与黄铜。全站只有这一处换装。 */
const isArchive = computed(() => route.path.startsWith('/archive'))

/**
 * 复审台是 Operate 面，不是 Experience 面 —— 它是干活的工具。
 * 所以它不铺星空：一片会闪的背景放在审核队列后面只会妨碍读字。
 */
const isAdmin = computed(() => route.path.startsWith('/admin'))

const crashed = ref(false)

// 启动认领身份：服务端按 deviceKey 认人，同一个浏览器永远拿到同一个昵称。
// 失败不阻塞浏览 —— 只有真要写东西时才需要身份。
onMounted(() => {
  bootstrap().catch(() => {})
})

onErrorCaptured((err) => {
  console.error('[星轨] 页面渲染失败：', err)
  crashed.value = true
  return false
})

function reload() {
  crashed.value = false
  window.location.reload()
}
</script>

<style scoped>
.shell {
  position: relative;
  min-height: 100dvh;
  isolation: isolate;
}

.sky {
  position: fixed;
  inset: 0;
  z-index: var(--z-stars);
  pointer-events: none;
}

.nebula {
  position: absolute;
  inset: 0;
  transition: opacity var(--dur-slow) var(--ease);
}

.nebula.cool {
  background:
    radial-gradient(900px 640px at 50% 38%, rgba(46, 76, 130, 0.20), transparent 70%),
    radial-gradient(620px 440px at 79% 80%, rgba(72, 53, 112, 0.13), transparent 72%),
    radial-gradient(700px 520px at 17% 20%, rgba(30, 62, 104, 0.13), transparent 70%);
}

.nebula.warm {
  opacity: 0;
  background:
    radial-gradient(880px 620px at 50% 40%, rgba(120, 88, 44, 0.22), transparent 70%),
    radial-gradient(560px 420px at 22% 82%, rgba(96, 58, 40, 0.14), transparent 72%),
    radial-gradient(700px 520px at 84% 18%, rgba(80, 66, 40, 0.13), transparent 70%);
}

.is-archive .nebula.cool {
  opacity: 0;
}

.is-archive .nebula.warm {
  opacity: 1;
}

.stars {
  position: absolute;
  inset: 0;
}

.vignette {
  position: absolute;
  inset: 0;
  background: radial-gradient(125% 105% at 50% 44%, transparent 42%, rgba(0, 0, 0, 0.74) 100%);
}

.stage {
  position: relative;
  z-index: var(--z-content);
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.is-admin {
  background: var(--void);
}

.is-admin .sky {
  display: none;
}

.crashed {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding-bottom: 80px;
  text-align: center;
}

.crashed h1 {
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
}

.crashed .lede {
  font-size: 14px;
  color: var(--ink-3);
}

.crashed .btn {
  margin-top: 12px;
}

/* 路由切换是一段安静的交叉淡入，不抢戏。
   全站真正"被设计"的那一次动效只有打捞。 */
.view-enter-active {
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease);
}

.view-leave-active {
  transition: opacity var(--dur-fast) var(--ease);
}

.view-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.view-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .view-enter-active,
  .view-leave-active {
    transition: none;
  }
}
</style>
