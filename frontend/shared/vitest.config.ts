import { defineConfig } from 'vitest/config'

/** 単体テストの設定。カバレッジの対象は、管理画面・店舗画面と同じ形にする（shadcn/ui の生成物は src/ui） */
export default defineConfig({
  test: {
    // jest-dom の照合の関数（toBeDisabled など）を使えるようにする
    setupFiles: ['@testing-library/jest-dom/vitest'],
    // カバレッジは報告と基準の検査に使う（テストの書き方の「カバレッジと mutation score の基準」）。shadcn/ui の生成物、テストの補助は数えない
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/ui/**', '**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'json-summary', 'html'],
    },
  },
})
