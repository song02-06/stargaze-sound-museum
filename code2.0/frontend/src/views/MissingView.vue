<template>
  <div class="missing">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink to="/collection">馆藏</RouterLink>
      </template>
    </AppTopBar>

    <div class="body">
      <p class="eyebrow">404</p>
      <h1 class="serif">这一段不在馆里</h1>
      <p class="lede">链接可能过期了，或者编号写错了。</p>
      <RouterLink class="btn primary" to="/">回到星空</RouterLink>
    </div>
  </div>
</template>

<script setup>
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
</script>

<style scoped>
.missing {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
}

.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 0 var(--rail) 80px;
  text-align: center;
}

.body h1 {
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
}

.lede {
  font-size: 14px;
  color: var(--ink-3);
}

.body .btn {
  margin-top: 14px;
}

.body .btn:hover {
  text-decoration: none;
}
</style>
