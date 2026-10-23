import { notFound } from 'next/navigation'
import type { Problem } from '@/api/types'
import { redirectToLogin } from '@/auth'
import { translate } from '@/i18n/translate'

/** API がエラーを返した（404 以外）。message はエラーレスポンスのもの（画面にそのまま出せる） */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, problem: Partial<Problem> | undefined) {
    super(problem?.message ?? translate('common.httpFailed', { status })) // 通信の失敗（HTTP の状態付き）
    this.name = 'ApiError'
    this.status = status
  }
}

/** openapi-fetch の結果 */
type FetchResult<T> = { data?: T; error?: unknown; response: Response }

/**
 * 成功なら本文を返す。404 は「見つかりません」の画面にし、それ以外の失敗は {@link ApiError} を投げる。
 */
export function unwrap<T>(result: FetchResult<T>): T {
  if (result.response.ok && result.data !== undefined) {
    return result.data
  }
  if (result.response.status === 404) {
    notFound()
  }
  throw new ApiError(result.response.status, result.error as Partial<Problem> | undefined)
}

/**
 * 会員の口の取得の結果を {@link unwrap} する。401（token が無効になった）なら、いまの画面に戻るように
 * ログインの画面へ移す（店舗画面の構成 第 9 節）。
 */
export async function unwrapMember<T>(result: FetchResult<T>): Promise<T> {
  if (result.response.status === 401) {
    await redirectToLogin()
  }
  return unwrap(result)
}
