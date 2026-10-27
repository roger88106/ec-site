import { unstable_rethrow } from 'next/navigation'
import { ApiError } from '@/api/result'
import { type ActionResult, connectionFailedMessage } from '@/lib/actionResult'
import { log } from '@/lib/server/log'

/**
 * サーバーアクションの処理を包み、例外を結果にする（フロントの書き方 第 3 節）。<br />
 * 画面の移動（`redirect`）と not found は例外で知らせるので、そのまま通す。それ以外の例外（接続の失敗など）は、
 * 操作した部品の近くに出せるよう、接続できなかったことを示す失敗の結果にし、error のログに出す。
 *
 * @param work 処理
 * @returns 処理の値。例外なら失敗の結果
 */
export async function withActionResult<T>(work: () => Promise<T>): Promise<T | Extract<ActionResult, { ok: false }>> {
  try {
    return await work()
  } catch (error) {
    // 画面の移動・not found の例外は、ここで飲み込まずに Next.js へ返す
    unstable_rethrow(error)
    // 例外の名前と、原因の例外の名前とコード、API の失敗の状態コードだけを出す。メッセージには、カートの ID などの識別子や
    // 要求の内容が入りうるので、例外と原因のどちらのメッセージも出さない
    const cause: unknown = error instanceof Error ? error.cause : undefined
    const causeCode = cause instanceof Error && 'code' in cause ? cause.code : undefined
    await log.error('サーバーアクションが失敗しました', {
      errorName: error instanceof Error ? error.name : typeof error,
      causeName: cause instanceof Error ? cause.name : null,
      causeCode: typeof causeCode === 'string' ? causeCode : null,
      // API の失敗の例外なら、その状態コード（識別子ではないので出してよい）
      statusCode: error instanceof ApiError ? error.status : null,
    })
    return { ok: false, message: connectionFailedMessage() }
  }
}
