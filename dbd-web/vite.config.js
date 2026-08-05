// Vite 配置：开发服务器 /api 代理到后端 8080（生产环境由 nginx 反代）
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发期：/api/* → http://localhost:8080/api/*
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
