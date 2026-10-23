import type { ErrorCode, Problem } from '@/api/types'
import { translate } from '@/i18n/translate'

/** バージョン不一致（他の人が先に更新した）のエラーコード */
const CONCURRENT_MODIFICATION: ErrorCode = 'CONCURRENT_MODIFICATION'

/**
 * API がエラーを返した（管理画面の構成 第 4 節）。<br />
 * 本文がエラーレスポンス（API の規約）なら {@link problem} に入る。プロキシのエラーなどで本文の形が違えば undefined。
 */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | undefined

  constructor(status: number, problem: Problem | undefined) {
    // 本文がエラーレスポンスでないときの文。403 は権限がない案内（バックエンドの FORBIDDEN と同じ文）
    super(
      problem?.message ??
        (status === 403
          ? translate('api.errors.forbidden' /* 権限がない案内 */)
          : translate('common.httpFailed', { status } /* 通信の失敗（HTTP の状態付き） */)),
    )
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
    // バージョン不一致は、再読み込みを促す案内にする
    return error.isConcurrentModification
      ? translate('api.errors.concurrentModification' /* 再読み込みの案内 */)
      : error.message
  }
  // fetch 自体の失敗（バックエンドが止まっているなど）
  return translate('common.connectionFailed') // サーバーに接続できない案内
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
