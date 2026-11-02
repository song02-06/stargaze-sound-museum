<template>
  <div class="collection">
    <AppTopBar>
      <template #left><BrandMark /></template>
      <template #right>
        <RouterLink class="is-current" to="/collection">馆藏</RouterLink>
        <RouterLink to="/archive">历史之声</RouterLink>
      </template>
    </AppTopBar>

    <header class="head rail">
      <p class="eyebrow">馆藏</p>
      <h1 class="serif">已经沉入星空的声音</h1>
      <p class="lede">
        每一件都有编号、时间、地点，和一段由留下它的人亲手写下的故事。
      </p>

      <!-- 检索只覆盖已通过的内容；关键词打在标题、故事、地点上都算 -->
      <form class="search" role="search" @submit.prevent="run">
        <input
          v-model="keyword"
          class="search-input"
          type="search"
          placeholder="搜标题、故事或地点…"
          aria-label="检索馆藏"
        />
        <button v-if="keyword" class="search-clear" type="button" @click="clear">清空</button>
        <button class="search-go" type="submit">找</button>
      </form>
    </header>

    <div class="rail">
      <StateNote v-if="loading">正在整理目录…</StateNote>
      <StateNote v-else-if="error" error :on-retry="run">{{ error }}</StateNote>
      <StateNote v-else-if="!items?.length && activeKeyword">
        没有找到和「{{ activeKeyword }}」有关的展品。
        <button class="inline-btn" type="button" @click="clear">清空条件</button>
      </StateNote>
      <StateNote v-else-if="!items?.length">馆藏还是空的。</StateNote>
      <template v-else>
        <p v-if="activeKeyword" class="result-note">
          「{{ activeKeyword }}」找到 {{ items.length }} 件。
        </p>
        <ul class="grid">
          <li v-for="item in items" :key="item.id">
            <RouterLink class="card" :to="`/exhibit/${item.no}`">
              <span class="no tnum">
                Nº {{ item.no }}
                <span v-if="item.seed" class="tag">演示</span>
              </span>
              <h2 class="serif">{{ item.title }}</h2>
              <p class="sign">{{ item.sign }}</p>
              <span class="meta">{{ item.place }} · {{ dotDate(item.date) }}</span>
            </RouterLink>
          </li>
        </ul>
      </template>
    </div>

    <footer class="foot rail">
      <p>带「演示」角标的是合成占位素材，不是真实录音；其余为用户投稿。</p>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import StateNote from '../components/StateNote.vue'
import { useAsync } from '../composables/useAsync.js'
import * as api from '../api/index.js'
import { dotDate } from '../lib/format.js'

const keyword = ref('')
/** 上一次真的发出去的关键词 —— 列表是按它取的，不是按输入框里正在打的字 */
const activeKeyword = ref('')

const { data: items, loading, error, run } = useAsync(() => {
  activeKeyword.value = keyword.value.trim()
  return api.exhibits(activeKeyword.value)
})

function clear() {
  keyword.value = ''
  run()
}
</script>

<style scoped>
.collection {
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  padding-bottom: 40px;
}

.head {
  padding-top: 60px;
  padding-bottom: 44px;
}

.head h1 {
  margin-top: 18px;
  font-size: clamp(24px, 3vw, 36px);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: var(--ink);
}

.lede {
  margin-top: 16px;
  max-width: var(--measure);
  font-size: 14px;
  font-weight: 300;
  line-height: 2;
  color: var(--ink-3);
}

/* ---------- 检索 ---------- */

.search {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  max-width: 34rem;
}

.search-input {
  flex: 1;
  height: 44px;
  padding: 0 16px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  font-size: 14px;
  color: var(--ink);
}

.search-input::placeholder {
  color: var(--ink-4);
}

.search-input:focus {
  border-color: var(--brass-2);
  outline: none;
}

.search-clear,
.search-go {
  flex: none;
  height: 44px;
  padding: 0 20px;
  border: 1px solid var(--line-2);
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
  transition: color var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease);
}

.search-go {
  border-color: var(--brass-2);
  color: var(--brass);
}

.search-clear:hover,
.search-go:hover {
  color: var(--ink);
  border-color: rgba(255, 255, 255, 0.3);
}

.search-go:hover {
  color: #e0c185;
}

.inline-btn {
  margin-left: 10px;
  font-size: 12px;
  color: var(--brass);
  border-bottom: 1px solid var(--brass-2);
}

.result-note {
  padding-bottom: 18px;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: var(--ink-4);
}

/* ---------- 卡片 ---------- */

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  list-style: none;
}

.card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 28px 26px 26px;
  border-top: 1px solid var(--line-2);
  background: linear-gradient(rgba(255, 255, 255, 0.018), transparent 60%);
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.card:hover {
  border-top-color: var(--brass-2);
  background: linear-gradient(rgba(201, 169, 107, 0.05), transparent 60%);
  text-decoration: none;
}

.no {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--brass);
}

/* 演示数据必须一眼看得出来 —— 不能让人以为那是真实的用户投稿 */
.tag {
  padding: 1px 6px;
  border: 1px solid var(--line-2);
  border-radius: 3px;
  font-family: var(--sans);
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--ink-4);
}

.card h2 {
  margin-top: 16px;
  font-size: 19px;
  font-weight: 400;
  letter-spacing: 0.04em;
  color: var(--ink);
}

.sign {
  flex: 1;
  margin-top: 14px;
  font-size: 13px;
  font-weight: 300;
  line-height: 1.95;
  color: var(--ink-2);
  text-wrap: pretty;
}

.meta {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--ink-4);
}

.foot {
  margin-top: auto;
  padding-top: 48px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--ink-4);
}

@media (max-width: 760px) {
  .head {
    padding-top: 40px;
    padding-bottom: 32px;
  }

  .grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
