<template>
  <header class="header">
    <div class="brand">
      <span class="dot" :class="{ off: !online }"></span>
      <h1>星轨 <span class="sep">·</span> 声音博物馆</h1>
    </div>

    <nav class="nav" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="nav-item"
        :class="{ active: modelValue === tab.key }"
        role="tab"
        :aria-selected="modelValue === tab.key"
        @click="$emit('update:modelValue', tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="tools">
      <span class="status" :title="statusTitle">
        <span class="pulse" :class="{ off: !online }"></span>
        {{ online ? `${total} 段馆藏` : '后端未连接' }}
      </span>
      <button class="icon-btn" :title="lowSpec ? '当前：低配模式' : '当前：完整画质'" @click="$emit('toggle-low-spec')">
        {{ lowSpec ? '低配' : '画质' }}
      </button>
    </div>
  </header>
</template>

<script setup>
defineProps({
  // v-model 默认绑定的就是 modelValue，
  // 之前这里写成 model 会触发 "Missing required prop" 警告并导致高亮失效
  modelValue: { type: String, required: true },
  online: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  lowSpec: { type: Boolean, default: false },
  statusTitle: { type: String, default: '' }
})

defineEmits(['update:modelValue', 'toggle-low-spec'])

const tabs = [
  { key: 'draw', label: '捞一段' },
  { key: 'record', label: '留一段' },
  { key: 'collection', label: '馆藏' }
]
</script>

<style scoped>
.header {
  position: relative;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 20px;
  height: var(--header-h);
  padding: 0 clamp(16px, 4vw, 44px);
  flex: none;
  pointer-events: auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand h1 {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.2em;
  color: var(--ink-2);
  white-space: nowrap;
}

.sep {
  color: var(--ink-3);
  margin: 0 2px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 14px var(--accent);
  flex: none;
}

.dot.off {
  background: var(--danger);
  box-shadow: 0 0 14px var(--danger);
}

.nav {
  display: flex;
  gap: 4px;
  margin: 0 auto;
  padding: 4px;
  border-radius: var(--r-pill);
  background: rgba(10, 15, 30, 0.6);
  border: 1px solid var(--line);
  backdrop-filter: blur(12px);
}

.nav-item {
  padding: 7px 20px;
  border-radius: var(--r-pill);
  font-size: 13px;
  letter-spacing: 0.08em;
  color: var(--ink-3);
  transition: color 0.2s ease, background 0.2s ease;
  white-space: nowrap;
}

.nav-item:hover {
  color: var(--ink-2);
}

.nav-item.active {
  color: #05101f;
  background: linear-gradient(120deg, rgba(159, 216, 255, 0.95), rgba(255, 207, 153, 0.85));
  font-weight: 600;
}

.tools {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--ink-3);
  white-space: nowrap;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7be3a8;
  box-shadow: 0 0 10px #7be3a8;
}

.pulse.off {
  background: var(--danger);
  box-shadow: 0 0 10px var(--danger);
}

.icon-btn {
  padding: 6px 14px;
  border-radius: var(--r-pill);
  font-size: 12px;
  color: var(--ink-3);
  border: 1px solid var(--line);
  transition: color 0.2s ease, border-color 0.2s ease;
}

.icon-btn:hover {
  color: var(--accent);
  border-color: var(--line-strong);
}

@media (max-width: 720px) {
  .header {
    gap: 10px;
    height: auto;
    padding: 10px 16px 8px;
    flex-wrap: wrap;
  }

  .brand h1 {
    font-size: 12px;
    letter-spacing: 0.12em;
  }

  .nav {
    order: 3;
    width: 100%;
    margin: 2px 0 0;
    justify-content: space-between;
    padding: 3px;
  }

  .nav-item {
    flex: 1;
    padding: 6px 6px;
    font-size: 12.5px;
  }

  .tools {
    margin-left: auto;
  }

  .status {
    display: none;
  }
}
</style>
