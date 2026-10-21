import type { Route } from 'next'
import { redirect } from 'next/navigation'
import { withActionResult } from '@/actions/withActionResult'
import { redirectToLogin } from '@/auth'
import type { ActionResult } from '@/lib/actionResult'

/** 失敗の結果 */
type Failure = Extract<ActionResult, { ok: false }>

/**
 * 会員の API の応答が 401（token が無効）なら、ログインの画面へ移す（フロントの書き方 第 3.2 節）。
 *
 * @param response API の応答
 * @param returnTo ログインの後に戻る画面。なければいまの画面
 */
export async function redirectIfUnauthenticated(response: Response, returnTo?: string): Promise<void> {
  if (response.status === 401) {
    await redirectToLogin(returnTo)
  }
}

/**
 * 処理を `withActionResult` で包み、移動先が得られたらその画面へ移す。失敗の結果はそのまま返す。<br />
 * `redirect` は例外で移動を知らせるので、包みの外で呼ぶ（フロントの書き方 第 3.1 節）。
 *
 * @param work 処理。成功したら移動先（外部の URL を含む）、失敗したら失敗の結果を返す
 * @returns 失敗の結果（成功したら移動するので戻らない）
 */
export async function redirectOnSuccess<T extends string>(work: () => Promise<Route<T> | Failure>): Promise<Failure> {
  const result = await withActionResult(work)
  if (typeof result !== 'string') {
    return result
  }
  redirect(result)
}
