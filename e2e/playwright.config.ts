import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'
import { ADMIN_URL, BACKEND_MANAGEMENT_URL, BACKEND_URL, IDP_URL, IMAGE_ORIGIN, portOf, STORE_URL } from './src/urls'
import { BACKEND_START_TIMEOUT, FRONTEND_START_TIMEOUT, TEST_TIMEOUT } from './src/waits'

/** CI で動いているか（test.only を禁止し、HTML のレポートを残す） */
const CI = Boolean(process.env.CI)
/** バックエンドの Gradle プロジェクト。Windows の cmd は作業ディレクトリのスクリプトを名前だけでは探さないので、絶対パスで呼ぶ */
const BACKEND_DIR = path.resolve(import.meta.dirname, '../backend/ecsite')
/** バックエンドを起動する Gradle Wrapper のパス */
const GRADLEW = path.join(BACKEND_DIR, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

/** 幅 375px の project で実行するテストのファイル */
const NARROW_TEST = '**/header-narrow.spec.ts'

/**
 * E2E（テストの実行方法の「テストの種類」）。バックエンド、管理画面、店舗画面を起動して、ブラウザーで操作する。<br />
 * E2E 用の DB・IdP・画像のバケットを使い、ローカルのデータを変えない。DB と IdP は、
 * 準備の script（scripts/prepare.ts）が毎回再作成する。
 * どのテストも同じ DB を使うので、1 つずつ順に動かす。テストのデータは実行ごとの識別子を付けて作る（src/data.ts）。
 * 画面のサーバーは、手元でも CI でも毎回起動し、起動済みのサーバーを使い回さない（ポートが使われていれば Playwright が止まる）。
 * 別の作業ツリーで起動したサーバーを使うと、その作業ツリーのコードを試すことになるため。<br />
 * ブラウザーの設定（project）は、デスクトップの幅（既定）と、幅 375px（狭い幅のテストのファイルだけ）に分ける。
 * 全てのテストを 2 つの幅で実行すると、実行時間がほぼ倍になるため。
 */
export default defineConfig({
  testDir: 'tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: CI,
  retries: 0,
  timeout: TEST_TIMEOUT,
  reporter: CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    ...devices['Desktop Chrome'],
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: NARROW_TEST },
    {
      name: 'narrow',
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } },
      testMatch: NARROW_TEST,
    },
  ],
  webServer: [
    {
      // E2E 用の設定（application-e2e.yml。8090、運用ポート 9090、ec_site_e2e、E2E 用の IdP）。
      // 再作成した DB につなぐため、手元でも毎回起動する（使い回さない）
      name: 'backend',
      command: `"${GRADLEW}" bootRun --args=--spring.profiles.active=local,e2e`,
      cwd: BACKEND_DIR,
      url: `${BACKEND_MANAGEMENT_URL}/actuator/health/readiness`,
      timeout: BACKEND_START_TIMEOUT,
      reuseExistingServer: false,
    },
    {
      // ビルドしたものを vite preview で配る。/api は E2E のバックエンドに転送する（vite.config.ts の server.proxy）。
      // IdP の接続先はビルドのときに埋め込む
      name: 'admin',
      command: `pnpm build && pnpm preview --port ${portOf(ADMIN_URL)} --strictPort`,
      cwd: '../frontend/admin',
      url: ADMIN_URL,
      env: {
        VITE_OIDC_AUTHORITY: `${IDP_URL}/realms/ec-admin`,
        VITE_OIDC_CLIENT_ID: 'ec-admin-web',
        ADMIN_API_PROXY_TARGET: BACKEND_URL,
      },
      timeout: FRONTEND_START_TIMEOUT,
      reuseExistingServer: false,
    },
    {
      // API の結果のキャッシュを 1 秒にし、本番の 60 秒のキャッシュを待たずに一覧への反映を確認する
      name: 'store',
      command: `pnpm build && pnpm start --port ${portOf(STORE_URL)}`,
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
        AUTH_URL: STORE_URL,
        STORE_IMAGE_ORIGIN: IMAGE_ORIGIN,
        STORE_MOCK_PAYMENT_ORIGIN: BACKEND_URL,
      },
      timeout: FRONTEND_START_TIMEOUT,
      reuseExistingServer: false,
    },
  ],
})
