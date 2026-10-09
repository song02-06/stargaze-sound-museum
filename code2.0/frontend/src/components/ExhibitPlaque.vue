<template>
  <div class="plaque">
    <h1 class="title serif">{{ exhibit.title }}</h1>
    <p class="meta tnum">
      Nº {{ exhibit.no }} · {{ dotDate(exhibit.date) }} · {{ exhibit.place }}
    </p>

    <!-- 展签与全文同一时刻只出现一份，一键切换。
         不做并排对比 —— 对比是编辑工具，不是展品界面。 -->
    <div class="text">
      <Transition name="swap" mode="out-in">
        <p :key="expanded ? 'full' : 'sign'" class="body serif">
          {{ expanded ? exhibit.full : exhibit.sign }}
        </p>
      </Transition>
    </div>

    <button v-if="exhibit.full" class="switch" type="button" @click="expanded = !expanded">
      {{ expanded ? '看展签' : '看全文' }}
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { dotDate } from '../lib/format.js'

defineProps({
  exhibit: { type: Object, required: true }
})

const expanded = ref(false)
</script>

<style scoped>
.plaque {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.title {
  font-size: clamp(28px, 3.2vw, 39px);
  font-weight: 400;
  line-height: 1.35;
  letter-spacing: 0.04em;
  text-wrap: balance;
  color: var(--ink);
}

.meta {
  margin-top: 18px;
  font-size: 12px;
  color: var(--brass);
}

.text {
  min-height: 8.6rem;
  margin-top: 28px;
  width: min(100%, var(--measure));
}

.body {
  font-size: 16px;
  font-weight: 300;
  line-height: 2.15;
  color: var(--ink-2);
  text-wrap: pretty;
}

.switch {
  margin-top: 28px;
  padding-bottom: 5px;
  border-bottom: 1px solid var(--brass-2);
  font-size: 11px;
  letter-spacing: 0.30em;
  text-indent: 0.30em;
  color: var(--brass);
  transition: border-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.switch:hover {
  border-color: var(--brass);
  color: #e0c185;
}

.swap-enter-active,
.swap-leave-active {
  transition: opacity 200ms var(--ease);
}

.swap-enter-from,
.swap-leave-to {
  opacity: 0;
}

@media (max-width: 760px) {
  .text {
    min-height: 7rem;
    margin-top: 22px;
  }

  .body {
    font-size: 15px;
    line-height: 2;
  }
}
</style>
