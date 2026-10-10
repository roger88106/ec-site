/** 通信の失敗（バックエンドが止まっているなど）のときの案内（店舗画面の構成 第 6 節） */
export const CONNECTION_FAILED = 'サーバーに接続できませんでした。'

/**
 * サーバーアクションの結果（店舗画面の構成 第 6 節）。<br />
 * 失敗は、画面に出す message と、エラーコード、入力欄ごとのメッセージ（項目名 → メッセージ）を持つ。
 */
export type ActionResult =
  | { ok: true }
  | { ok: false; message: string; errorCode?: string; fieldErrors?: Record<string, string> }

/** 成功 */
export const SUCCESS: ActionResult = { ok: true }

/**
 * API のエラーの応答を、失敗の結果にする。message はエラーレスポンスのもの（画面にそのまま出せる）。
 *
 * @param status HTTP の状態
 * @param body   エラーの応答の本文
 */
export function toFailure(status: number, body: unknown): Extract<ActionResult, { ok: false }> {
  if (typeof body !== 'object' || body === null || !('message' in body) || typeof body.message !== 'string') {
    return { ok: false, message: `通信に失敗しました（HTTP ${status}）` }
  }
  const problem = body as { message: string; errorCode?: string; errors?: { field: string; message: string }[] }
  const fieldErrors = Object.fromEntries((problem.errors ?? []).map((error) => [error.field, error.message]))
  return {
    ok: false,
    message: problem.message,
    ...(problem.errorCode && { errorCode: problem.errorCode }),
    ...(Object.keys(fieldErrors).length > 0 && { fieldErrors }),
  }
}
