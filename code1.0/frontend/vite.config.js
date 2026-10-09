import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期用 vite 代理转发，比在后端开 CORS 更省事（后端那份 CORS 保留作为兜底）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/audio': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
