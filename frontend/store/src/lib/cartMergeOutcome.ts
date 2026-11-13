/**
 * カートを移した結果の区分。
 * - merged：移せた。Cookie のカートの ID を削除する
 * - gone：移すカートがない、または ID が不正。再試行しても結果が変わらないので、ID を削除する
 * - unauthenticated：ログインが切れている。ID を残す
 * - failed：一時的な失敗（5xx、例外、契約にない応答）。ID を残す
 */
export type CartMergeOutcome = 'merged' | 'gone' | 'unauthenticated' | 'failed'

/**
 * 移す API（POST /me/cart/merge）の HTTP ステータスから、結果の区分を返す。
 *
 * @param status HTTP ステータス
 * @returns 結果の区分
 */
export function cartMergeOutcome(status: number): CartMergeOutcome {
  if (status >= 200 && status < 300) {
    return 'merged'
  }
  if (status === 404 || status === 400) {
    return 'gone'
  }
  if (status === 401) {
    return 'unauthenticated'
  }
  return 'failed'
}
