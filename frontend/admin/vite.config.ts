/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // 開発時は API をバックエンド（local profile で起動したもの）に転送する
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // 結合テスト（管理画面の構成 第 10 節）のため jsdom で動かす。単体テストもそのまま動く
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
  },
})
