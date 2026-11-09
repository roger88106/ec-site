import 'server-only'
import { deleteCartId, readCartId, writeCartMergeNotice } from '@/api/cartCookie'
import { getMemberApi } from '@/api/client'
import { unwrapMember } from '@/api/result'
import { currentMember } from '@/auth'
import { type CartChoice, chooseCart, shouldMergeCart } from '@/lib/cartChoice'
import { type CartMergeNotice, cartMergeNotice } from '@/lib/cartMergeNotice'
import { cartMergeOutcome } from '@/lib/cartMergeOutcome'
import { log } from '@/lib/server/log'

/**
 * 使うカートを選ぶ（店舗画面の構成の「使うカートの選び方」）。
 * ログインしていれば会員のカート、していなければ Cookie のカート。
 */
export async function currentCartChoice(): Promise<CartChoice> {
  return chooseCart((await currentMember()) !== null, await readCartId())
}

/**
 * 会員のカートの ID（GET /me/cart。なければ API が作る）。401 なら、ログインの画面へ移す。
 */
export async function memberCartId(): Promise<string> {
  const api = await getMemberApi()
  return (await unwrapMember(await api.GET('/me/cart'))).cartId
}

/** カートを移した結果 */
export type CartMergeResult =
  /** 移せた。notice は、切り詰めた品目と移せなかった品目がなければ null */
  | { kind: 'merged'; notice: CartMergeNotice | null }
  /** 移すカートがない、または ID が不正（再試行しても結果が変わらない） */
  | { kind: 'gone' }
  /** 一時的な失敗（5xx、例外、契約にない応答、2xx で本文のない応答） */
  | { kind: 'failed' }
  /** ログインが切れている */
  | { kind: 'unauthenticated' }

/**
 * Cookie のカートを会員のカートへ移す（POST /me/cart/merge）。<br />
 * 移す前にカートの内容を取っておき、切り詰めた品目と移せなかった品目の名前を、結果の知らせに入れる。
 * 例外は failed にする。移せなかったときは警告のログを書く。
 *
 * @param cartId  Cookie のカート ID
 * @param headers 認証のヘッダー。なければセッションの token を使う
 * @returns 移した結果。Cookie の操作は呼び出し側が行う
 */
export async function mergeCart(cartId: string, headers?: Record<string, string>): Promise<CartMergeResult> {
  try {
    const api = await getMemberApi()
    const source = await api.GET('/carts/{cartId}', { params: { path: { cartId } }, headers })
    const { data, response } = await api.POST('/me/cart/merge', { body: { cartId }, headers })
    const outcome = cartMergeOutcome(response.status)
    if (outcome === 'merged' && data) {
      return { kind: 'merged', notice: cartMergeNotice(data, source.data?.items ?? []) }
    }
    // カートの ID は Cookie の値で、ゲストのカートは ID を知れば操作できるため、ログに出力しない
    await log.warn('カートを移せませんでした', { status: response.status })
    // 2xx で本文がないのは契約にない応答なので、一時的な失敗にする
    return outcome === 'merged' ? { kind: 'failed' } : { kind: outcome }
  } catch (error) {
    // 例外のメッセージに URL（カートの ID）が入りうるため、例外の名前だけを残す
    await log.warn('カートを移せませんでした', { error: error instanceof Error ? error.name : typeof error })
    return { kind: 'failed' }
  }
}

/**
 * ログインの直後に、Cookie のカートを会員のカートへ移す。Auth.js の signIn のイベントから呼ぶ。<br />
 * 移せたときと、移すカートがないときは、Cookie のカート ID を消す（移せたときは移すカートが空になり、移すカートがないときは再試行しても結果が変わらないため）。
 * 移せたときは、切り詰めた品目と移せなかった品目の名前を、知らせの Cookie に書く。
 * 一時的な失敗とログインが切れているときは、カート ID を残し、知らせの Cookie も書かない（カートの画面を開くと、移す処理を再試行する）。
 *
 * @param accessToken ログインで得た access token（セッションの Cookie はまだ書かれていない）
 */
export async function mergeCartAfterLogin(accessToken: string): Promise<void> {
  const cartId = await readCartId()
  if (!shouldMergeCart(cartId)) {
    return
  }
  const result = await mergeCart(cartId, { Authorization: `Bearer ${accessToken}` })
  if (result.kind === 'merged' && result.notice !== null) {
    await writeCartMergeNotice(result.notice)
  }
  if (result.kind === 'merged' || result.kind === 'gone') {
    await deleteCartId()
  }
}
