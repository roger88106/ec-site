import { defineConfig, devices } from '@playwright/test'
import { SHOWCASE_TEST_TIMEOUT } from './src/waits'

/**
 * 機能紹介のページ（showcase/）のスクリーンショットの撮影。<br />
 * E2E ではない。手元で起動しているローカルの環境（compose、profile local のバックエンド、店舗画面、管理画面。
 * URL は src/showcase-urls.ts で、E2E のサーバーとはポートが別）の画面を、local の seed のデータで撮る。
 * サーバーは起動しない。CI では流さない。
 * 使い方：e2e/ で pnpm capture（出力は showcase/img/ に上書きする）
 */
export default defineConfig({
  testDir: 'showcase',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: SHOWCASE_TEST_TIMEOUT,
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    viewport: { width: 1280, height: 800 },
    colorScheme: 'light',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },
})
