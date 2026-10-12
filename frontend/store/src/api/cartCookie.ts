import 'server-only'
import { cookies } from 'next/headers'
import { cartCookieOptions, parseCartId } from '@/lib/cartId'
import { type CartMergeNotice, parseCartMergeNotice, serializeCartMergeNotice } from '@/lib/cartMergeNotice'

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

/**
 * カート ID の Cookie を消す（ログアウト・カートを移した後。店舗画面の構成 第 4.2 節）。サーバーアクションと
 * ルートハンドラーからだけ呼べる。
 */
export async function deleteCartId(): Promise<void> {
  ;(await cookies()).delete(CART_COOKIE)
}

/** カートを移した結果の知らせを持つ Cookie の名前（店舗画面の構成 第 4.2 節） */
const CART_MERGE_NOTICE_COOKIE = 'cart_merge_notice'

/**
 * カートを移した結果の知らせを読む。サーバーコンポーネントから呼ぶ。
 *
 * @returns 知らせ。なければ null
 */
export async function readCartMergeNotice(): Promise<CartMergeNotice | null> {
  return parseCartMergeNotice((await cookies()).get(CART_MERGE_NOTICE_COOKIE)?.value)
}

/**
 * カートを移した結果の知らせを書く（ブラウザーを閉じるまで。表示したら消す）。ルートハンドラーからだけ呼べる。
 *
 * @param notice 知らせ
 */
export async function writeCartMergeNotice(notice: CartMergeNotice): Promise<void> {
  const { maxAge: _maxAge, ...options } = cartCookieOptions(process.env.NODE_ENV === 'production')
  ;(await cookies()).set(CART_MERGE_NOTICE_COOKIE, serializeCartMergeNotice(notice), options)
}

/** カートを移した結果の知らせを消す（表示した後）。サーバーアクションからだけ呼べる */
export async function deleteCartMergeNotice(): Promise<void> {
  ;(await cookies()).delete(CART_MERGE_NOTICE_COOKIE)
}
