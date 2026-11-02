<template>
  <div class="login-page">
    <AppTopBar><template #left><BrandMark /></template><template #right><RouterLink to="/">返回星空</RouterLink></template></AppTopBar>
    <section class="login-panel" aria-labelledby="login-title">
      <h1 id="login-title" class="serif">{{ mode === 'register' ? '在星空里留个名字' : '欢迎回到星空' }}</h1>
      <p class="intro">{{ mode === 'register' ? '注册后，当前游客身份的声音与回音会保留在新账号中。' : '登录账号，找回你埋下的声音和收到的回音。' }}</p>
      <template v-if="loggedIn">
        <p class="intro">你已登录 {{ state.session.username }}。</p>
        <RouterLink class="btn primary" to="/mine">进入我的声音</RouterLink>
      </template>
      <form v-else @submit.prevent="submit" :aria-busy="busy">
        <label for="username">用户名</label>
        <input ref="usernameInput" id="username" v-model="username" name="username" autocomplete="username" required pattern="[A-Za-z0-9_]{3,24}" minlength="3" maxlength="24" aria-describedby="username-help" :disabled="busy">
        <p id="username-help" class="help">3–24 位字母、数字或下划线，不区分大小写</p>
        <label for="password">密码</label>
        <input id="password" v-model="password" name="password" type="password" :autocomplete="mode === 'register' ? 'new-password' : 'current-password'" required :minlength="mode === 'register' ? 8 : undefined" maxlength="128" aria-describedby="password-help" :disabled="busy">
        <p id="password-help" class="help">{{ mode === 'register' ? '8–128 个字符，请妥善保管密码' : '输入注册时使用的密码' }}</p>
        <template v-if="mode === 'register'">
          <label for="confirm">确认密码</label>
          <input id="confirm" v-model="confirmPassword" name="confirm" type="password" autocomplete="new-password" required minlength="8" maxlength="128" :disabled="busy">
        </template>
        <p class="error" role="alert">{{ error }}</p>
        <button class="btn primary submit" type="submit" :disabled="busy">{{ busy ? '正在提交…' : mode === 'register' ? '注册并登录' : '登录' }}</button>
        <button class="switch" type="button" :disabled="busy" @click="switchMode">{{ mode === 'register' ? '已有账号？去登录' : '还没有账号？注册一个' }}</button>
      </form>
      <RouterLink class="guest" to="/">继续以游客身份浏览</RouterLink>
    </section>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTopBar from '../components/AppTopBar.vue'
import BrandMark from '../components/BrandMark.vue'
import { authenticate, loggedIn, state } from '../store/museum.js'
const router = useRouter()
const route = useRoute()
const mode = ref('login')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const busy = ref(false)
const usernameInput = ref(null)
async function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  password.value = ''
  confirmPassword.value = ''
  error.value = ''
  await nextTick()
  usernameInput.value?.focus()
}
async function submit() {
  if (busy.value) return
  error.value = ''
  if (mode.value === 'register' && password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    await authenticate(mode.value, username.value.trim(), password.value)
    password.value = ''
    confirmPassword.value = ''
    const next = typeof route.query.next === 'string' ? route.query.next : '/mine'
    await router.replace(next.startsWith('/') && !next.startsWith('//') ? next : '/mine')
  } catch (e) { error.value = e.message || '暂时无法登录，请稍后再试' }
  finally { busy.value = false }
}
</script>

<style scoped>
.login-page { min-height: 100dvh; }
.login-panel { width: 440px; margin: 72px auto 100px; }
h1 { font-size: 32px; font-weight: 400; letter-spacing: .06em; color: var(--ink); }
.intro { margin: 16px 0 30px; color: var(--home-muted); font-size: 14px; }
form { display: flex; flex-direction: column; }
label { margin-bottom: 8px; color: var(--ink); font-size: 14px; }
input { width: 100%; padding: 12px 14px; border: 1px solid var(--home-muted); border-radius: 8px; background: #0d1420; font-size: 16px; }
input:focus-visible { outline: 2px solid var(--brass); outline-offset: 3px; }
.help { margin: 7px 0 22px; color: var(--home-muted); font-size: 13px; }
.error { min-height: 28px; margin-top: 12px; color: #ffb6aa; font-size: 14px; }
.submit { margin-top: 8px; border-color: var(--brass); }
.switch { align-self: center; min-height: 44px; margin-top: 14px; color: var(--ink); font-size: 14px; text-decoration: underline; text-underline-offset: 5px; }
.guest { display: block; width: fit-content; margin: 24px auto 0; padding: 8px; color: var(--home-muted); font-size: 14px; text-decoration: underline; }
</style>
