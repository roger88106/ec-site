/// <reference types="vitest/config" />
import path from 'node:path'
import { LOCALE } from '@ec-site/shared/lib/locale'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

/** index.html のページの言語（%LOCALE%）を、共有のロケールにする */
const htmlLocale: Plugin = {
  name: 'html-locale',
  transformIndexHtml: (html) => html.replace('%LOCALE%', LOCALE),
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), htmlLocale],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // 開発時は API をバックエンド（local profile で起動したもの）に転送する。vite preview も同じ転送先を使う。
    // E2E は、E2E 用のバックエンド（8090）を ADMIN_API_PROXY_TARGET で渡す
    proxy: {
      '/api': process.env.ADMIN_API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    // 結合テスト（管理画面の構成 第 9 節）のため jsdom で動かす。単体テストもそのまま動く
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    // カバレッジは報告だけ（テストの書き方 第 1 節）。生成物と shadcn/ui の生成物、テストの補助は数えない
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/api/generated/**', 'src/components/ui/**', 'src/test/**', '**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'json-summary', 'html'],
    },
  },
})
