import type { Problem } from '@/api/types'

/** バージョン不一致（他の人が先に更新した）のエラーコード */
const CONCURRENT_MODIFICATION = 'CONCURRENT_MODIFICATION'

/** 権限がない（403）のときの案内。本文がエラーレスポンスでないときに使う（AC-141。バックエンドの FORBIDDEN と同じ文） */
const FORBIDDEN_MESSAGE = 'この操作を行う権限がありません。'

/** バージョン不一致のときの案内（T703） */
export const CONCURRENT_MODIFICATION_MESSAGE = '他の人が先に更新しました。再読み込みしてください'

/**
 * API がエラーを返した（管理画面の構成 第 4 節）。<br />
 * 本文がエラーレスポンス（constitution 9）なら {@link problem} に入る。プロキシのエラーなどで本文の形が違えば undefined。
 */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | undefined

  constructor(status: number, problem: Problem | undefined) {
    super(problem?.message ?? (status === 403 ? FORBIDDEN_MESSAGE : `通信に失敗しました（HTTP ${status}）`))
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** バージョン不一致（409） */
  get isConcurrentModification(): boolean {
    return this.status === 409 && this.problem?.errorCode === CONCURRENT_MODIFICATION
  }

  /** 4xx（入力の誤り、見つからない、競合など）。繰り返しても結果が変わらない */
  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500
  }
}

/** エラーの応答の本文から {@link ApiError} を作る */
export function toApiError(status: number, body: unknown): ApiError {
  return new ApiError(status, asProblem(body))
}

/** 画面に出すメッセージ。エラーレスポンスの message をそのまま出す */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.isConcurrentModification ? CONCURRENT_MODIFICATION_MESSAGE : error.message
  }
  // fetch 自体の失敗（バックエンドが止まっているなど）
  return 'サーバーに接続できませんでした。'
}

/** 入力検証エラーの項目ごとのメッセージ */
export function fieldErrors(error: unknown): { field: string; message: string }[] {
  return error instanceof ApiError ? (error.problem?.errors ?? []) : []
}

/** 応答の本文が Problem Details の形（message を持つ）なら、その型で返す */
function asProblem(body: unknown): Problem | undefined {
  if (typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string') {
    return body as Problem
  }
  return undefined
}
