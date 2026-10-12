import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'
import { ADMIN_URL, BACKEND_URL, IDP_URL, STORE_URL } from './src/urls'

/** CI で動いているか（test.only を禁止し、HTML のレポートを残し、起動済みの画面のサーバーを使い回さない） */
const CI = Boolean(process.env.CI)
/** バックエンドの Gradle プロジェクト。Windows の cmd は作業ディレクトリのスクリプトを名前だけでは探さないので、絶対パスで呼ぶ */
const BACKEND_DIR = path.resolve(import.meta.dirname, '../backend/ecsite')
/** バックエンドを起動する Gradle Wrapper のパス */
const GRADLEW = path.join(BACKEND_DIR, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

/**
 * E2E（constitution 12）。バックエンド、管理画面、店舗画面を起動して、ブラウザで操作する。<br />
 * E2E 用の DB・IdP・画像のバケットを使い、ローカルのデータを変えない（plan 004 第 6.1 節）。DB と IdP は、
 * 準備の script（scripts/prepare.ts）が毎回作り直す。
 * どのテストも同じ DB を使うので、1 つずつ順に動かす。テストのデータは実行ごとの印を付けて作る（src/data.ts）。
 * 手元では、すでに起動している画面のサーバーがあればそれを使う。
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
      // E2E 用の設定（application-e2e.yml。8090、ec_site_e2e、E2E 用の IdP）。
      // 作り直した DB につなぐため、手元でも毎回起動する（使い回さない）
      name: 'backend',
      command: `"${GRADLEW}" bootRun --args=--spring.profiles.active=local,e2e`,
      cwd: BACKEND_DIR,
      url: `${BACKEND_URL}/actuator/health/readiness`,
      timeout: 300_000,
      reuseExistingServer: false,
    },
    {
      // ビルドしたものを vite preview で配る。/api は E2E のバックエンドに転送する（vite.config.ts の server.proxy）。
      // IdP の接続先はビルドのときに埋め込む
      name: 'admin',
      command: 'pnpm build && pnpm preview --port 4173 --strictPort',
      cwd: '../frontend/admin',
      url: ADMIN_URL,
      env: { VITE_OIDC_AUTHORITY: `${IDP_URL}/realms/ec-admin`, ADMIN_API_PROXY_TARGET: BACKEND_URL },
      timeout: 180_000,
      reuseExistingServer: !CI,
    },
    {
      // API の結果のキャッシュを 1 秒にし、NFR-1 の 60 秒を待たずに一覧への反映を確かめる
      name: 'store',
      command: 'pnpm build && pnpm start --port 3100',
      cwd: '../frontend/store',
      url: STORE_URL,
      env: {
        STORE_API_REVALIDATE_SECONDS: '1',
        STORE_API_BASE_URL: `${BACKEND_URL}/api/store`,
        AUTH_KEYCLOAK_ISSUER: `${IDP_URL}/realms/ec-store`,
        // next start は本番のモードなので、必須の値をすべて渡す（値は realm の JSON と同じ、ローカルと E2E だけの値）
        AUTH_KEYCLOAK_ID: 'ec-store-web',
        AUTH_KEYCLOAK_SECRET: 'ec-store-web-local-secret',
        AUTH_SECRET: 'ec-store-local-auth-secret-only-for-local-and-ci',
      },
      timeout: 180_000,
      reuseExistingServer: !CI,
    },
  ],
})
