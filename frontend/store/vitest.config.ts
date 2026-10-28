import path from 'node:path'
import { defineConfig } from 'vitest/config'

/** 単体テストの設定。@/ の別名を tsconfig.json に合わせる */
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    // 既定の数（論理 CPU − 1）が物理コアより多いと 1 テストの時間が延び、タイムアウトで失敗する
    maxWorkers: '50%',
    // サーバーの処理のテストのための置き換えと、API の代わりのサーバー（店舗画面の構成 第 7 節）
    setupFiles: ['src/test/setup.ts'],
    // next-auth は拡張子なしで next/server を読むので、Vite の解決を通す（ログインの戻り先のテストは本物の Auth.js を使う）
    server: { deps: { inline: ['next-auth'] } },
    // カバレッジは報告と基準の検査に使う（テストの書き方 第 1.2 節）。生成物と shadcn/ui の生成物、テストの補助は数えない
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/api/generated/**', 'src/components/ui/**', 'src/test/**', '**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'json-summary', 'html'],
    },
  },
})
