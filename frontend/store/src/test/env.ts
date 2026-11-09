/**
 * 環境変数のテストで使う値（フロントのテストの書き方の「店舗画面のテストは、対象ごとの方法で書く」）。
 */

/** 本番のモードで渡す環境変数のすべて。テストで確認する項目は、スプレッドで上書きするか、分割代入で除く */
export const PRODUCTION_ENV = {
  NODE_ENV: 'production',
  AUTH_SECRET: 'secret',
  AUTH_KEYCLOAK_SECRET: 'client-secret',
  AUTH_KEYCLOAK_ISSUER: 'https://idp.example/realms/ec-store',
  AUTH_KEYCLOAK_ID: 'ec-store-web',
  STORE_API_BASE_URL: 'https://api.example/api/store',
  AUTH_URL: 'https://shop.example',
  STORE_IMAGE_ORIGIN: 'https://img.example',
}
