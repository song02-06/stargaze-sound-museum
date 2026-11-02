<template>
  <div ref="meteor" class="meteor-cursor" aria-hidden="true">
    <span class="meteor-tail" />
    <span class="meteor-core" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const meteor = ref(null)
let preference, frame = 0, x = 0, y = 0, tailX = 0, tailY = 0, lastTime = 0
let visible = false
function hide() {
  visible = false
  cancelAnimationFrame(frame)
  frame = lastTime = 0
  document.documentElement.classList.remove('has-meteor-cursor')
  if (meteor.value) meteor.value.style.opacity = '0'
}
function paint(time) {
  const dt = lastTime ? Math.min((time - lastTime) / 1000, .05) : 1 / 60
  lastTime = time
  const dx = x - tailX, dy = y - tailY
  const distance = Math.hypot(dx, dy)
  meteor.value.style.transform = `translate3d(${x}px, ${y}px, 0) rotate(${Math.atan2(dy, dx)}rad)`
  meteor.value.style.setProperty('--tail-length', `${Math.min(100, distance * 1.3) + 2}px`)
  const blend = 1 - Math.exp(-12 * dt)
  tailX += dx * blend
  tailY += dy * blend
  if (distance > .1) frame = requestAnimationFrame(paint)
  else { frame = lastTime = 0 }
}
function move(event) {
  if (event.pointerType !== 'mouse' || !preference.matches) return
  x = event.clientX
  y = event.clientY
  if (!visible) {
    tailX = x - 12
    tailY = y + 8
    visible = true
    meteor.value.style.opacity = '1'
    document.documentElement.classList.add('has-meteor-cursor')
  }
  if (!frame) frame = requestAnimationFrame(paint)
}
onMounted(() => {
  preference = matchMedia('(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)')
  preference.addEventListener('change', hide)
  window.addEventListener('pointermove', move, { passive: true })
  window.addEventListener('blur', hide)
  document.documentElement.addEventListener('pointerleave', hide)
})
onUnmounted(() => {
  hide()
  preference?.removeEventListener('change', hide)
  window.removeEventListener('pointermove', move)
  window.removeEventListener('blur', hide)
  document.documentElement.removeEventListener('pointerleave', hide)
})
</script>

<style>
html.has-meteor-cursor, html.has-meteor-cursor * { cursor: none !important; }
.meteor-cursor { position: fixed; top: 0; left: 0; width: 0; height: 0; z-index: 10000; pointer-events: none; opacity: 0; color: #fff; }
.meteor-core { position: absolute; width: 7px; height: 7px; left: -3.5px; top: -3.5px; border-radius: 50%; background: #fff; box-shadow: 0 0 6px 2px rgb(255 255 255 / .85), 0 0 16px 5px rgb(220 234 255 / .35); }
.meteor-tail { position: absolute; right: 0; top: -1.5px; height: 3px; width: var(--tail-length, 2px); border-radius: 50%; background: linear-gradient(90deg, transparent, rgb(224 236 255 / .3) 40%, #fff); transform-origin: right center; }

</style>
