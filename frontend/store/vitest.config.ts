import path from 'node:path'
import { defineConfig } from 'vitest/config'

/** 単体テストの設定。@/ の別名を tsconfig.json に合わせる */
export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
})
