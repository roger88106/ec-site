import { defineConfig } from '@playwright/test'
import showcase from './playwright.showcase.config'

/**
 * 設計書（docs/01-概要/）に載せる、画面の操作の流れのスクリーンショットの撮影。<br />
 * E2E ではない。撮り方と前提（手元のローカルの環境、local の seed のデータ）は機能紹介の撮影（playwright.showcase.config.ts）と同じ。
 * サーバーは起動しない。CI では流さない。
 * 使い方：e2e/ で pnpm capture:docs（出力は docs/01-概要/img/ の下に上書きする）
 */
export default defineConfig({
  ...showcase,
  testDir: 'docs',
})
