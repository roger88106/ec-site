/// <reference types="vitest/config" />
import path from 'node:path'
import { LOCALE, TIME_ZONE } from '@ec-site/shared/lib/locale'
import common from '@ec-site/shared/messages/ja.json' with { type: 'json' }
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { createTranslator } from 'use-intl/core'
import { defineConfig, type Plugin } from 'vite'
import admin from './messages/ja.json' with { type: 'json' }

/** 文言の関数（index.html の題名に使う。画面と同じく、共有の文言と管理画面の文言を浅く合わせる） */
const translate = createTranslator({ locale: LOCALE, timeZone: TIME_ZONE, messages: { ...common, ...admin } })

/** index.html のページの言語（%LOCALE%）と題名（%TITLE%）を、共有のロケールと文言にする */
const htmlLocale: Plugin = {
  name: 'html-locale',
  transformIndexHtml: (html) =>
    html
      .replace('%LOCALE%', LOCALE)
      .replace('%TITLE%', translate('app.layout.title', { siteName: translate('common.siteName') })),
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
