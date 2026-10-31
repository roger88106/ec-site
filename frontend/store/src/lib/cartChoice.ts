/** 使うカート（店舗画面の構成の「使うカートの選び方」） */
export type CartChoice =
  /** 会員のカート（GET /me/cart の ID。会員の token を付けて操作する） */
  | { kind: 'member' }
  /** Cookie のカート */
  | { kind: 'cookie'; cartId: string }
  /** カートがない（「カートに入れる」のときに作る） */
  | { kind: 'none' }

/**
 * 使うカートを選ぶ。ログインしていれば、Cookie のカートがあっても会員のカートを使う。
 *
 * @param loggedIn     ログインしているか
 * @param cookieCartId Cookie のカート ID。なければ null
 */
export function chooseCart(loggedIn: boolean, cookieCartId: string | null): CartChoice {
  if (loggedIn) {
    return { kind: 'member' }
  }
  return cookieCartId === null ? { kind: 'none' } : { kind: 'cookie', cartId: cookieCartId }
}

/**
 * ログインの直後に、Cookie のカートを会員のカートへ移すか。
 *
 * @param cookieCartId Cookie のカート ID。なければ null
 */
export function shouldMergeCart(cookieCartId: string | null): cookieCartId is string {
  return cookieCartId !== null
}
