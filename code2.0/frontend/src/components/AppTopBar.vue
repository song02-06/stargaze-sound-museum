<template>
  <header class="topbar rail">
    <div class="side">
      <slot name="left" />
    </div>
    <div class="side right">
      <slot name="right" />
    </div>
  </header>
  <div class="hairline rail-line"></div>
</template>

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
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
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
