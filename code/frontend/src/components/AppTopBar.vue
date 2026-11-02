<template>
  <header class="topbar rail">
    <div class="side">
      <slot name="left" />
    </div>
    <nav class="side right" aria-label="主导航">
      <!-- 未读角标放在每个页面的导航前面：
           它是「关于我」的提示，不属于某一页 -->
      <UnreadBadge v-if="loggedIn" />
      <slot name="right" />
      <RouterLink v-if="!loggedIn" to="/login">登录</RouterLink>
      <RouterLink v-else to="/mine" :aria-label="`我的声音，当前账号：${state.session.username}`">我的声音</RouterLink>
    </nav>
  </header>
  <div class="hairline rail-line"></div>
</template>

<script setup>
import UnreadBadge from './UnreadBadge.vue'
import { loggedIn, state } from '../store/museum.js'
</script>

<style scoped>
.topbar {
  position: relative;
  z-index: var(--z-bar);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  height: 78px;
}

.side {
  display: flex;
  align-items: center;
  gap: 28px;
  font-size: 14px;
  letter-spacing: 0.14em;
  color: var(--home-nav);
}

.right {
  justify-content: flex-end;
}

.rail-line {
  margin-inline: var(--rail);
}

:deep(a),
:deep(button) {
  color: inherit;
  transition: color var(--dur-fast) var(--ease);
}

:deep(a) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

:deep(a:hover),
:deep(button:hover) {
  color: var(--ink);
  text-decoration: none;
}

:deep(.right a) { position: relative; min-height: 32px; }

:deep(.right a.router-link-active)::after,
:deep(.right a.is-current)::after {
  content: ""; position: absolute; inset: auto 0 -7px; height: 1px;
  background: var(--brass); box-shadow: 0 0 8px rgb(201 169 107 / .6);
}

:deep(.is-current) {
  color: var(--ink);
}

:deep(.is-brass) {
  color: var(--brass);
}

@media (max-width: 760px) {
  .topbar {
    height: 64px;
  }

  .side {
    gap: 18px;
    font-size: 11px;
    letter-spacing: 0.10em;
  }
}
</style>
