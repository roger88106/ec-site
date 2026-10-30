import 'server-only'
import { deleteCartId, readCartId, writeCartMergeNotice } from '@/api/cartCookie'
import { getMemberApi } from '@/api/client'
import { unwrapMember } from '@/api/result'
import { currentMember } from '@/auth'
import { type CartChoice, chooseCart, shouldMergeCart } from '@/lib/cartChoice'
import { cartMergeNotice } from '@/lib/cartMergeNotice'
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

/**
 * ログインの直後に、Cookie のカートを会員のカートへ移す。Auth.js の signIn のイベントから呼ぶ。<br />
 * 移す前にカートの内容を取っておき、切り詰めた品目と移せなかった品目の名前を、知らせの Cookie に書く。
 * 移せなかったときも Cookie のカート ID は消す（ログインしている間は使わず、残すとログアウトの後に古いカートが見えるため）。
 *
 * @param accessToken ログインで得た access token（セッションの Cookie はまだ書かれていない）
 */
export async function mergeCartAfterLogin(accessToken: string): Promise<void> {
  const cartId = await readCartId()
  if (!shouldMergeCart(cartId)) {
    return
  }
  try {
    const api = await getMemberApi()
    const headers = { Authorization: `Bearer ${accessToken}` }
    const source = await api.GET('/carts/{cartId}', { params: { path: { cartId } }, headers })
    const { data, response } = await api.POST('/me/cart/merge', { body: { cartId }, headers })
    if (data) {
      const notice = cartMergeNotice(data, source.data?.items ?? [])
      if (notice !== null) {
        await writeCartMergeNotice(notice)
      }
    } else {
      // カートの ID は Cookie の値で、ゲストのカートは ID を知れば操作できるため、ログに出力しない
      await log.warn('カートを移せませんでした', { status: response.status })
    }
  } catch (error) {
    // 例外のメッセージに URL（カートの ID）が入りうるため、例外の名前だけを残す
    await log.warn('カートを移せませんでした', { error: error instanceof Error ? error.name : typeof error })
  }
  await deleteCartId()
}
