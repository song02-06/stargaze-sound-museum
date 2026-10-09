import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 换后端地址不用动组件代码：设 MUSEUM_BACKEND 就行
// （联调时拿它把开发服务器指到另一个实例上）
const BACKEND = process.env.MUSEUM_BACKEND || 'http://127.0.0.1:8081'

const proxy = {
  '/api': { target: BACKEND, changeOrigin: true },
  '/audio': { target: BACKEND, changeOrigin: true }
}

export default defineConfig({
  plugins: [vue()],
  // 开发和预览都走代理：前端只认 /api 和 /audio，
  // 换后端地址不用动任何组件代码。
  server: { port: 5174, host: '127.0.0.1', proxy },
  preview: { port: 4180, host: '127.0.0.1', proxy }
})
