import { unstable_rethrow } from 'next/navigation'
import { type ActionResult, connectionFailedMessage } from '@/lib/actionResult'

/**
 * サーバーアクションの処理を包み、例外を結果にする（フロントの書き方 第 3 節）。<br />
 * 画面の移動（`redirect`）と not found は例外で知らせるので、そのまま通す。それ以外の例外（接続の失敗など）は、
 * 操作した部品の近くに出せるよう、接続できなかったことを示す失敗の結果にする。
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
    return { ok: false, message: connectionFailedMessage() }
  }
}
