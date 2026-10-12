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
    // カバレッジは報告だけ（constitution 12.1）。生成物と shadcn/ui の生成物、テストの補助は数えない
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/api/generated/**', 'src/components/ui/**', 'src/test/**', '**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'json-summary', 'html'],
    },
  },
})
