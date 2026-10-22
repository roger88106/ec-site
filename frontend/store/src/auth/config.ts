/**
 * 店舗画面のサーバーの設定（フロントの書き方 第 8.1 節）。<br />
 * 本番（`NODE_ENV=production`）では、必須の値がなければ起動に失敗させる（`instrumentation.ts`）。
 * ローカル用の既定の値（realm の JSON と同じ）は、開発のモードとテストでだけ使う。
 */

/** 本番で必須の環境変数 */
const REQUIRED_IN_PRODUCTION = [
  'AUTH_SECRET',
  'AUTH_KEYCLOAK_SECRET',
  'AUTH_KEYCLOAK_ISSUER',
  'AUTH_KEYCLOAK_ID',
  'STORE_API_BASE_URL',
  'AUTH_URL',
] as const

/** 開発のモードとテストで使う、ローカル用の既定の値 */
const LOCAL_DEFAULTS: Record<(typeof REQUIRED_IN_PRODUCTION)[number], string> = {
  AUTH_SECRET: 'ec-store-local-auth-secret-only-for-local-and-ci',
  AUTH_KEYCLOAK_SECRET: 'ec-store-web-local-secret',
  AUTH_KEYCLOAK_ISSUER: 'http://localhost:8180/realms/ec-store',
  AUTH_KEYCLOAK_ID: 'ec-store-web',
  STORE_API_BASE_URL: 'http://localhost:8080/api/store',
  AUTH_URL: 'http://localhost:3000',
}

/** 店舗 API の公開の口の結果をキャッシュする秒数の既定（変更は 1 分以内に店舗画面へ反映する） */
const DEFAULT_REVALIDATE_SECONDS = 60

/** 店舗 API の応答を待つ秒数の既定（バックエンドで最も長く待つ支払画面を開く操作より長くする） */
const DEFAULT_API_TIMEOUT_SECONDS = 30

/** IdP の応答を待つ秒数の既定 */
const DEFAULT_IDP_TIMEOUT_SECONDS = 10

/** 待つ時間の上限の環境変数（正の数でなければ起動に失敗させる） */
const TIMEOUT_SETTINGS = ['STORE_API_TIMEOUT_SECONDS', 'AUTH_KEYCLOAK_TIMEOUT_SECONDS'] as const

/** 店舗画面のサーバーの設定 */
export type ServerConfig = {
  /** 消費者の realm の issuer */
  issuer: string
  /** 店舗画面の client（confidential）の ID */
  clientId: string
  /** 店舗画面の client の secret */
  clientSecret: string
  /** セッションの Cookie を暗号化する鍵 */
  secret: string
  /** 店舗 API の場所（`/api/store` まで） */
  apiBaseUrl: string
  /** 店舗画面を公開する URL（origin）。ログイン・ログアウトの戻り先を作る（Auth.js も同じ環境変数 AUTH_URL を読む） */
  publicUrl: string
  /** 店舗 API の公開の口の結果をキャッシュする秒数（任意。既定 60） */
  apiRevalidateSeconds: number
  /** 店舗 API の応答を待つ秒数（任意。既定 30） */
  apiTimeoutSeconds: number
  /** IdP（ログイン、token の更新）の応答を待つ秒数（任意。既定 10） */
  idpTimeoutSeconds: number
  /** 店舗画面の全ての Cookie の名前の前に付ける接頭辞（任意。既定は空で、今の名前のまま） */
  cookiePrefix: string
}

/** 0 以上の整数（再取得の間隔に使える値） */
const NON_NEGATIVE_INTEGER_PATTERN = /^\d+$/

/** Cookie の接頭辞に使える文字（英数字と `-`、`_`） */
const COOKIE_PREFIX_PATTERN = /^[A-Za-z0-9_-]*$/

/**
 * 環境変数から設定を読む。本番のモードで必須の値がなければ（空も含む）、足りない名前を並べた例外を投げる。
 * 待つ時間の上限が正の数でなければ、モードによらず、その名前を並べた例外を投げる。
 * 再取得の間隔が 0 以上の整数でなければ、同じくモードによらず例外を投げる。
 *
 * @param env 環境変数
 * @returns 設定
 */
export function loadServerConfig(env: Record<string, string | undefined> = process.env): ServerConfig {
  const production = env.NODE_ENV === 'production'
  const missing = REQUIRED_IN_PRODUCTION.filter((name) => !env[name])
  if (production && missing.length > 0) {
    throw new Error(`店舗画面の起動に必要な環境変数がありません：${missing.join(', ')}`)
  }
  const invalid = TIMEOUT_SETTINGS.filter((name) => env[name] && !(Number(env[name]) > 0))
  if (invalid.length > 0) {
    throw new Error(`待つ時間の上限は正の数にしてください：${invalid.join(', ')}`)
  }
  const revalidate = env.STORE_API_REVALIDATE_SECONDS
  if (revalidate && !NON_NEGATIVE_INTEGER_PATTERN.test(revalidate)) {
    throw new Error('再取得の間隔は 0 以上の整数にしてください（0 はキャッシュしない）：STORE_API_REVALIDATE_SECONDS')
  }
  if (!COOKIE_PREFIX_PATTERN.test(env.STORE_COOKIE_PREFIX ?? '')) {
    throw new Error('Cookie の接頭辞は英数字と - _ だけにしてください：STORE_COOKIE_PREFIX')
  }
  return readServerConfig(env)
}

/**
 * 環境変数から設定を読む。確認はせず、値がなければローカル用の既定の値にする。<br />
 * モジュールの読み込みで使う（`next build` でも読まれるため）。本番で値がないときは、起動のとき
 * （{@link loadServerConfig}）にすでに失敗しているので、既定の値で動くことはない。
 *
 * @param env 環境変数
 * @returns 設定
 */
export function readServerConfig(env: Record<string, string | undefined> = process.env): ServerConfig {
  const value = (name: (typeof REQUIRED_IN_PRODUCTION)[number]) => env[name] || LOCAL_DEFAULTS[name]
  return {
    issuer: value('AUTH_KEYCLOAK_ISSUER'),
    clientId: value('AUTH_KEYCLOAK_ID'),
    clientSecret: value('AUTH_KEYCLOAK_SECRET'),
    secret: value('AUTH_SECRET'),
    apiBaseUrl: value('STORE_API_BASE_URL'),
    publicUrl: value('AUTH_URL'),
    apiRevalidateSeconds: Number(env.STORE_API_REVALIDATE_SECONDS || DEFAULT_REVALIDATE_SECONDS),
    apiTimeoutSeconds: Number(env.STORE_API_TIMEOUT_SECONDS || DEFAULT_API_TIMEOUT_SECONDS),
    idpTimeoutSeconds: Number(env.AUTH_KEYCLOAK_TIMEOUT_SECONDS || DEFAULT_IDP_TIMEOUT_SECONDS),
    cookiePrefix: env.STORE_COOKIE_PREFIX ?? '',
  }
}
