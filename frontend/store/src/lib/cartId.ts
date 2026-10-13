import { isUuid } from '@/lib/uuid'

/** カートの有効期限（最後の変更から 30 日）と同じ、Cookie の有効期限（秒） */
const MAX_AGE_SECONDS = 30 * 24 * 60 * 60

/** カートを使う操作が、カートが見つからなかった（404）ことを知らせる値 */
export const CART_NOT_FOUND = Symbol('CART_NOT_FOUND')

/**
 * Cookie の値をカート ID にする（店舗画面の構成 第 4.2 節）。
 *
 * @param value Cookie の値
 * @returns カート ID。Cookie がない・形の違う値なら null（カートがない）
 */
export function parseCartId(value: string | undefined): string | null {
  return value !== undefined && isUuid(value) ? value : null
}

/**
 * カート ID の Cookie の属性（店舗画面の構成 第 4.2 節）。ブラウザの JavaScript からは読めない。
 *
 * @param secure HTTPS だけで送るか（本番では true）
 */
export function cartCookieOptions(secure: boolean) {
  return { httpOnly: true, sameSite: 'lax', secure, path: '/', maxAge: MAX_AGE_SECONDS } as const
}

/**
 * カートを使う操作を行う。カートがなければ作り、見つからなければ作り直して 1 回だけやり直す。
 *
 * @param cartId 今のカート ID。なければ null
 * @param create カートを作り、その ID を返す
 * @param run    操作。カートが見つからなければ {@link CART_NOT_FOUND} を返す
 * @returns 操作に使ったカート ID、カートを作ったか（Cookie を書き直すか）、操作の結果
 */
export async function withCart<T>(
  cartId: string | null,
  create: () => Promise<string>,
  run: (cartId: string) => Promise<T | typeof CART_NOT_FOUND>,
): Promise<{ cartId: string; created: boolean; value: T }> {
  if (cartId !== null) {
    const value = await run(cartId)
    if (value !== CART_NOT_FOUND) {
      return { cartId, created: false, value }
    }
  }
  const createdId = await create()
  const value = await run(createdId)
  if (value === CART_NOT_FOUND) {
    throw new Error(`作ったカートが見つかりません（${createdId}）`)
  }
  return { cartId: createdId, created: true, value }
}
