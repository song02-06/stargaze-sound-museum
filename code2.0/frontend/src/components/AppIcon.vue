<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    :stroke-width="weight"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <g v-if="name === 'play'">
      <path d="M5.6 3.4 12.5 8l-6.9 4.6z" />
    </g>
    <g v-else-if="name === 'pause'">
      <path d="M6.3 3.6v8.8" />
      <path d="M9.7 3.6v8.8" />
    </g>
    <g v-else-if="name === 'send'">
      <path d="M8 13.2V3.4" />
      <path d="M4.2 7.2 8 3.4l3.8 3.8" />
    </g>
    <g v-else-if="name === 'chevron-left'">
      <path d="M9.9 3.3 5.2 8l4.7 4.7" />
    </g>
    <g v-else-if="name === 'chevron-right'">
      <path d="M6.1 3.3 10.8 8l-4.7 4.7" />
    </g>
    <!-- 打捞：一条垂线，末端勾住一个光点。和幕布上的动效同一套几何。 -->
    <g v-else-if="name === 'draw'">
      <path d="M8 2.4v6.9" />
      <circle cx="8" cy="11.6" r="2.1" />
    </g>
    <!-- 埋下：和「发送」同一支箭，方向相反 —— 一个往上说给别人，一个往下沉进星空 -->
    <g v-else-if="name === 'bury'">
      <path d="M8 2.8v9.4" />
      <path d="M4.2 8.6 8 12.4l3.8-3.8" />
    </g>
  </svg>
</template>

<script setup>
/**
 * 全站唯一的图标来源。
 * 一套图标 = 一种笔画粗细、一种端点样式，不允许出现字符图标或 CSS 三角。
 */
defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 16 },
  weight: { type: [Number, String], default: 1.4 }
})
</script>

<style scoped>
.icon {
  display: block;
  flex: none;
}
</style>
