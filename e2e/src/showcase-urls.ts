/**
 * 機能紹介の撮影（playwright.showcase.config.ts）が使う、手元のローカルの環境の URL。
 * E2E のサーバー（urls.ts）とはポートが別で、E2E の URL の検査（local-ports）の対象にしないため、別のファイルに置く。
 */

/** ローカルのバックエンド（mock の物流の画面）の URL */
export const SHOWCASE_BACKEND_URL = 'http://localhost:8080'

/** ローカルの店舗画面（開発のサーバー）の URL */
export const SHOWCASE_STORE_URL = 'http://localhost:3000'

/** ローカルの管理画面（開発のサーバー）の URL */
export const SHOWCASE_ADMIN_URL = 'http://localhost:5173'
