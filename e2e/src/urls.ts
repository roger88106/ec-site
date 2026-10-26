/** E2E で起動するサーバーの URL（playwright.config.ts）。ローカルの開発のサーバーとはポートを分ける（plan 004 第 6.1 節） */
export const BACKEND_URL = 'http://localhost:8090'
/** E2E のバックエンドの運用ポート（死活監視。application-e2e.yml） */
export const BACKEND_MANAGEMENT_URL = 'http://localhost:9090'
/** 管理画面（vite preview）の URL */
export const ADMIN_URL = 'http://localhost:4173'
/** 店舗画面（next start）の URL */
export const STORE_URL = 'http://localhost:3100'
/** E2E 用の IdP（Keycloak。compose の profile e2e）の URL */
export const IDP_URL = 'http://localhost:8181'
/** IdP が送るメールを受ける Mailpit（compose）の画面と API の URL */
export const MAILPIT_URL = 'http://localhost:8025'

/**
 * URL のポート。サーバーの起動の設定（`--port`）を、URL の定数から決めるために使う。
 *
 * @param url ポートを含む URL
 * @returns ポート番号の文字列
 */
export function portOf(url: string): string {
  return new URL(url).port
}
