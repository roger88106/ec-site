import 'server-only'
import { cookies } from 'next/headers'
import { readServerConfig } from '@/auth/config'
import { cookieName } from '@/auth/cookieNames'
import { cartCookieOptions, parseCartId } from '@/lib/cartId'
import { type CartMergeNotice, parseCartMergeNotice, serializeCartMergeNotice } from '@/lib/cartMergeNotice'

/** Cookie の名前の接頭辞（環境変数 `STORE_COOKIE_PREFIX`。モジュールの読み込み時に 1 回だけ読む） */
const { cookiePrefix: COOKIE_PREFIX } = readServerConfig()

/** カート ID を持つ Cookie の名前（接頭辞は環境変数 `STORE_COOKIE_PREFIX`。空なら `cart_id` のまま） */
const cartCookie = () => cookieName(COOKIE_PREFIX, 'cart_id')

/**
 * Cookie のカート ID を読む（店舗画面の構成 第 4.4 節）。サーバーコンポーネントとサーバーアクションから呼ぶ。
 *
 * @returns カート ID。Cookie がない・形の違う値なら null
 */
export async function readCartId(): Promise<string | null> {
  return parseCartId((await cookies()).get(cartCookie())?.value)
}

/**
 * カート ID を Cookie に書く。有効期限も 30 日に延びる。サーバーアクションからだけ呼べる。
 *
 * @param cartId カート ID
 */
export async function writeCartId(cartId: string): Promise<void> {
  ;(await cookies()).set(cartCookie(), cartId, cartCookieOptions(process.env.NODE_ENV === 'production'))
}

/**
 * カート ID の Cookie を消す（ログアウト・カートを移した後。店舗画面の構成 第 4.4 節）。サーバーアクションと
 * ルートハンドラーからだけ呼べる。
 */
export async function deleteCartId(): Promise<void> {
  ;(await cookies()).delete(cartCookie())
}

/** カートを移した結果の知らせを持つ Cookie の名前（店舗画面の構成 第 4.5 節。接頭辞は {@link cartCookie} と同じ） */
const cartMergeNoticeCookie = () => cookieName(COOKIE_PREFIX, 'cart_merge_notice')

/**
 * カートを移した結果の知らせを読む。サーバーコンポーネントから呼ぶ。
 *
 * @returns 知らせ。なければ null
 */
export async function readCartMergeNotice(): Promise<CartMergeNotice | null> {
  return parseCartMergeNotice((await cookies()).get(cartMergeNoticeCookie())?.value)
}

/**
 * カートを移した結果の知らせを書く（ブラウザーを閉じるまで。表示したら消す）。ルートハンドラーからだけ呼べる。
 *
 * @param notice 知らせ
 */
export async function writeCartMergeNotice(notice: CartMergeNotice): Promise<void> {
  const { maxAge: _maxAge, ...options } = cartCookieOptions(process.env.NODE_ENV === 'production')
  ;(await cookies()).set(cartMergeNoticeCookie(), serializeCartMergeNotice(notice), options)
}

/** カートを移した結果の知らせを消す（表示した後）。サーバーアクションからだけ呼べる */
export async function deleteCartMergeNotice(): Promise<void> {
  ;(await cookies()).delete(cartMergeNoticeCookie())
}
