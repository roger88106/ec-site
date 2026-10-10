import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'
import { ADMIN_URL, BACKEND_URL, STORE_URL } from './src/urls'

/** CI で動いているか（test.only を禁止し、HTML のレポートを残し、起動済みのサーバーを使い回さない） */
const CI = Boolean(process.env.CI)
/** バックエンドの Gradle プロジェクト。Windows の cmd は作業ディレクトリのスクリプトを名前だけでは探さないので、絶対パスで呼ぶ */
const BACKEND_DIR = path.resolve(import.meta.dirname, '../../backend')
/** バックエンドを起動する Gradle Wrapper のパス */
const GRADLEW = path.join(BACKEND_DIR, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

/**
 * E2E（constitution 12）。バックエンド、管理画面、店舗画面を起動して、ブラウザで操作する。<br />
 * どのテストも同じ DB を使うので、1 つずつ順に動かす。テストのデータは実行ごとの印を付けて作る（src/data.ts）。
 * 手元では、すでに起動しているサーバーがあればそれを使う。
 */
export default defineConfig({
  testDir: 'tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: CI,
  retries: 0,
  // 店舗画面のキャッシュの反映を待つテストがあるので、既定（30 秒）より長くする
  timeout: 60_000,
  reporter: CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    ...devices['Desktop Chrome'],
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      // 先に compose の PostgreSQL を起動しておく（README の「E2E」）
      name: 'backend',
      command: `"${GRADLEW}" bootRun --args=--spring.profiles.active=local`,
      cwd: BACKEND_DIR,
      url: `${BACKEND_URL}/actuator/health/readiness`,
      timeout: 300_000,
      reuseExistingServer: !CI,
    },
    {
      // ビルドしたものを vite preview で配る。/api はバックエンドに転送する（vite.config.ts の server.proxy）
      name: 'admin',
      command: 'pnpm build && pnpm preview --port 4173 --strictPort',
      cwd: '../admin',
      url: ADMIN_URL,
      timeout: 180_000,
      reuseExistingServer: !CI,
    },
    {
      // API の結果のキャッシュを 1 秒にし、NFR-1 の 60 秒を待たずに一覧への反映を確かめる
      name: 'store',
      command: 'pnpm build && pnpm start --port 3100',
      cwd: '../store',
      url: STORE_URL,
      env: { STORE_API_REVALIDATE_SECONDS: '1' },
      timeout: 180_000,
      reuseExistingServer: !CI,
    },
  ],
})
