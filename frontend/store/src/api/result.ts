import { notFound } from 'next/navigation'
import type { Problem } from '@/api/types'

/** API がエラーを返した（404 以外）。message はエラーレスポンスのもの（画面にそのまま出せる） */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, problem: Partial<Problem> | undefined) {
    super(problem?.message ?? `通信に失敗しました（HTTP ${status}）`)
    this.name = 'ApiError'
    this.status = status
  }
}

/** openapi-fetch の結果 */
type FetchResult<T> = { data?: T; error?: unknown; response: Response }

/**
 * 成功なら本文を返す。404 は「見つかりません」の画面にし（AC-65）、それ以外の失敗は {@link ApiError} を投げる。
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
