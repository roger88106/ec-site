/**
 * 本番ビルドで必須の、IdP（運営者の realm）の環境変数（管理画面の構成 第 10 節）。<br />
 * 管理画面はビルドのときに接続先を埋め込むので、足りないまま本番ビルドすると、ローカルの IdP につなぐ画面ができる。
 */
const REQUIRED_IN_PRODUCTION_BUILD = ['VITE_OIDC_AUTHORITY', 'VITE_OIDC_CLIENT_ID'] as const

/**
 * 本番ビルドのときに、IdP の環境変数を確認する。足りなければ（空も含む）、足りない名前を並べた例外を投げる。<br />
 * 開発のサーバー、テスト、本番でないモードのビルドでは確認しない（ローカルの IdP の既定値を使う）。
 *
 * @param command Vite のコマンド（`build` か `serve`）
 * @param mode    Vite のモード
 * @param env     環境変数
 */
export function checkOidcEnv(command: string, mode: string, env: Record<string, string | undefined>): void {
  if (command !== 'build' || mode !== 'production') {
    return
  }
  const missing = REQUIRED_IN_PRODUCTION_BUILD.filter((name) => !env[name])
  if (missing.length > 0) {
    throw new Error(`管理画面の本番ビルドに必要な環境変数がありません：${missing.join(', ')}`)
  }
}
