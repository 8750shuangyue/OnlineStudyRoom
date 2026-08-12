import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // REST 接口转发到后端
      '/api': 'http://localhost:8081',
      // WebSocket 转发到后端（ws: true 启用升级）
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    exclude: ['e2e/**', 'node_modules/**', 'dist/**']
  }
})
