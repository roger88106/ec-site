import 'server-only'
import { cookies } from 'next/headers'
import { cartCookieOptions, parseCartId } from '@/lib/cartId'

/** カート ID を持つ Cookie の名前 */
const CART_COOKIE = 'cart_id'

/**
 * Cookie のカート ID を読む（店舗画面の構成 第 4.2 節）。サーバーコンポーネントとサーバーアクションから呼ぶ。
 *
 * @returns カート ID。Cookie がない・形の違う値なら null
 */
export async function readCartId(): Promise<string | null> {
  return parseCartId((await cookies()).get(CART_COOKIE)?.value)
}

/**
 * カート ID を Cookie に書く。有効期限も 30 日に延びる。サーバーアクションからだけ呼べる。
 *
 * @param cartId カート ID
 */
export async function writeCartId(cartId: string): Promise<void> {
  ;(await cookies()).set(CART_COOKIE, cartId, cartCookieOptions(process.env.NODE_ENV === 'production'))
}
