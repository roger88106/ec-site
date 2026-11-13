import { asProblem } from '@/api/problem'
import type { ErrorCode } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 通信の失敗（バックエンドが止まっているなど）のときの案内（店舗画面の構成の「結果の知らせ方」） */
export function connectionFailedMessage(): string {
  return translate('common.connectionFailed') // サーバーに接続できない案内
}

/**
 * サーバーアクションの結果（店舗画面の構成の「結果の知らせ方」）
 *
 * 失敗は、画面に出す message と、エラーコード、入力欄ごとのメッセージ（項目名 → メッセージ）を持つ。
 */
export type ActionResult =
  | { ok: true }
  | { ok: false; message: string; errorCode?: ErrorCode; fieldErrors?: Record<string, string> }

/** 成功 */
export const SUCCESS: ActionResult = { ok: true }

/**
 * API のエラーの応答を、失敗の結果にする。
 *
 * message はエラーレスポンスのもの（画面にそのまま出せる）。
 *
 * @param status HTTP の状態
 * @param body   エラーの応答の本文
 * @returns 失敗の結果
 */
export function toFailure(status: number, body: unknown): Extract<ActionResult, { ok: false }> {
  const problem = asProblem(body)
  if (problem === undefined) {
    return { ok: false, message: translate('common.httpFailed', { status }) } // 通信の失敗（HTTP の状態付き）
  }
  const fieldErrors = Object.fromEntries((problem.errors ?? []).map((error) => [error.field, error.message]))
  return {
    ok: false,
    message: problem.message,
    ...(problem.errorCode && { errorCode: problem.errorCode }),
    ...(Object.keys(fieldErrors).length > 0 && { fieldErrors }),
  }
}
