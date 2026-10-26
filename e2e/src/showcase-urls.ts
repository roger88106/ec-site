// 機能紹介の撮影（playwright.showcase.config.ts）が使う、手元のローカルの環境の URL。
// E2E のサーバー（urls.ts）とはポートが別で、E2E のポートだけを許す検査（local-ports の SOURCE_FILES）に掛けないため、別のファイルに置く。
// ポートは local-ports の CONFIG_FILES が一覧と照合する。

/** ローカルのバックエンド（mock の物流の画面）の URL */
export const SHOWCASE_BACKEND_URL = 'http://localhost:8080'

/** ローカルの店舗画面（開発のサーバー）の URL */
export const SHOWCASE_STORE_URL = 'http://localhost:3000'

/** ローカルの管理画面（開発のサーバー）の URL */
export const SHOWCASE_ADMIN_URL = 'http://localhost:5173'
