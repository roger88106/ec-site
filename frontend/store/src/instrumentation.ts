import { loadServerConfig } from '@/auth/config'

/**
 * サーバーの起動のときに 1 回だけ呼ばれる（Next.js の決まり）。<br />
 * 本番で必須の設定がなければ、ここで起動に失敗させる。モジュールの読み込みで確かめないのは、
 * `next build` も本番のモードで動き、CI のビルドには secret がないため（フロントの書き方 第 8.1 節）。
 */
export function register() {
  // Edge のランタイムには process.exit がない。設定を読むのは Node のサーバーだけ
  if (process.env.NEXT_RUNTIME !== 'nodejs') {
    return
  }
  try {
    loadServerConfig()
  } catch (error) {
    // 例外を投げるだけでは、Next.js はエラーを出したまま要求を受け続ける。プロセスを止めて、起動の失敗にする
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`)
    process.exit(1)
  }
}
