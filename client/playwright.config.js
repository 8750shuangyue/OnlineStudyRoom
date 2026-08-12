import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60000,
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: 'http://localhost:5173',
    // 使用系统已安装的浏览器，避免下载 Playwright 自带浏览器
    channel: process.env.PW_CHANNEL || 'msedge',
    trace: 'retain-on-failure'
  }
})
