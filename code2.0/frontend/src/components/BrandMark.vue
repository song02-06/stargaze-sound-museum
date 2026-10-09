<template>
  <RouterLink to="/" class="brand">
    <b>星轨</b>
    <span>·</span>
    <span>声音博物馆</span>
  </RouterLink>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-family: var(--serif);
  font-size: 13px;
  letter-spacing: 0.30em;
  color: var(--ink-2);
}

.brand b {
  font-weight: 500;
  color: var(--ink);
}

.brand span {
  color: var(--ink-4);
}

.brand:hover {
  text-decoration: none;
  color: var(--ink);
}

@media (max-width: 760px) {
  .brand {
    font-size: 12px;
    letter-spacing: 0.18em;
    gap: 5px;
  }
}
</style>
