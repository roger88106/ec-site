/** 使うカート（店舗画面の構成の「使うカートの選び方」） */
export type CartChoice =
  /** 会員のカート（GET /me/cart の ID。会員の token を付けて操作する） */
  | { kind: 'member' }
  /** Cookie のカート */
  | { kind: 'cookie'; cartId: string }
  /** カートがない（「カートに入れる」のときに作る） */
  | { kind: 'none' }

/**
 * 使うカートを選ぶ。
 *
 * ログインしていれば、Cookie のカートがあっても会員のカートを使う。
 *
 * @param loggedIn     ログインしているか
 * @param cookieCartId Cookie のカート ID。なければ null
 * @returns 使うカート
 */
export function chooseCart(loggedIn: boolean, cookieCartId: string | null): CartChoice {
  if (loggedIn) {
    return { kind: 'member' }
  }
  return cookieCartId === null ? { kind: 'none' } : { kind: 'cookie', cartId: cookieCartId }
}

/**
 * 移す Cookie のカートがあるかを判定する。
 *
 * ログインの直後（{@link mergeCartAfterLogin}）に、Cookie のカートを会員のカートへ移すかの判定に使う。
 *
 * @param cookieCartId Cookie のカート ID。なければ null
 * @returns ID があれば true
 */
export function shouldMergeCart(cookieCartId: string | null): cookieCartId is string {
  return cookieCartId !== null
}

/**
 * カートの画面で、カートを移す処理を再試行するかを判定する。
 *
 * 再試行するのは、ログインしていて Cookie のカート ID があるとき（ログインしている間に ID が残るのは、移せていないときだけ）。
 *
 * @param loggedIn     ログインしているか
 * @param cookieCartId Cookie のカート ID。なければ null
 * @returns 再試行するなら true
 */
export function shouldRetryCartMerge(loggedIn: boolean, cookieCartId: string | null): boolean {
  return loggedIn && shouldMergeCart(cookieCartId)
}
