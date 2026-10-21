import { defineConfig, devices } from '@playwright/test'

/**
 * 機能紹介のページ（showcase/）のスクリーンショットの撮影（spec 008 の plan 第 2 節）。<br />
 * E2E ではない。手元で起動しているローカルの環境（compose、バックエンド :8080 の profile local、店舗画面 :3000、
 * 管理画面 :5173）の画面を、local の seed のデータで撮る。サーバーは起動しない。CI では流さない。
 * 使い方：e2e/ で pnpm capture（出力は showcase/img/ に上書きする）
 */
export default defineConfig({
  testDir: 'showcase',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 180_000,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    viewport: { width: 1280, height: 800 },
    colorScheme: 'light',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },
})
