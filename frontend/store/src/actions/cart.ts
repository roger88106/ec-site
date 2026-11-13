'use server'

import { revalidatePath } from 'next/cache'
import { redirectIfUnauthenticated, redirectOnSuccess } from '@/actions/memberAction'
import { withActionResult, withFallback } from '@/actions/withActionResult'
import { currentCartChoice, memberCartId, mergeCart } from '@/api/cart'
import { deleteCartId, deleteCartMergeNotice, readCartId, writeCartId } from '@/api/cartCookie'
import { getApi, getMemberApi } from '@/api/client'
import { ApiError } from '@/api/result'
import type { Problem } from '@/api/types'
import { currentMember, redirectToLogin } from '@/auth'
import { translate } from '@/i18n/translate'
import { type ActionResult, SUCCESS, toFailure } from '@/lib/actionResult'
import { shouldRetryCartMerge } from '@/lib/cartChoice'
import { CART_NOT_FOUND, withCart } from '@/lib/cartId'
import type { CartMergeRetryResult } from '@/lib/cartMergeRetryResult'
import {
  PENDING_ORDER_CONFIRMATION_REQUIRED,
  PENDING_ORDER_LIMIT_EXCEEDED,
  PENDING_ORDERS_PATH,
  pendingOrdersLimitHref,
} from '@/lib/pendingOrders'
import { readIntOrNaN, readString } from '@/lib/server/formData'

/**
 * カートに入れる。
 *
 * ログインしていれば会員のカートに入れる。していなければ Cookie のカートに入れ、カートがなければ作って
 * Cookie に書く。フォームの項目：variantId、quantity。
 */
export async function addToCart(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const variantId = readString(formData, 'variantId')
  const quantity = readIntOrNaN(formData, 'quantity')
  return withActionResult(async () => {
    const choice = await currentCartChoice()
    if (choice.kind === 'member') {
      return await updateMemberCart(async (api, cartId) =>
        api.POST('/carts/{cartId}/items', { params: { path: { cartId } }, body: { variantId, quantity } }),
      )
    }
    const api = await getApi()
    const { cartId, value } = await withCart(
      choice.kind === 'cookie' ? choice.cartId : null,
      createCart,
      async (id) => {
        const result = await api.POST('/carts/{cartId}/items', {
          params: { path: { cartId: id } },
          body: { variantId, quantity },
        })
        return result.response.status === 404 ? CART_NOT_FOUND : result
      },
    )
    // 再作成したときは新しい ID を、そうでなくても有効期限を延ばすために書く
    await writeCartId(cartId)
    if (!value.response.ok) {
      return toFailure(value.response.status, value.error)
    }
    revalidatePath('/cart')
    return SUCCESS
  })
}

/**
 * 品目の数量を変える。フォームの項目：variantId、quantity。
 */
export async function changeCartItemQuantity(
  _previous: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const variantId = readString(formData, 'variantId')
  const quantity = readIntOrNaN(formData, 'quantity')
  return updateCart(async (api, cartId) =>
    api.PUT('/carts/{cartId}/items/{variantId}', { params: { path: { cartId, variantId } }, body: { quantity } }),
  )
}

/**
 * 品目を削除する。フォームの項目：variantId。
 */
export async function removeCartItem(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const variantId = readString(formData, 'variantId')
  return updateCart(async (api, cartId) =>
    api.DELETE('/carts/{cartId}/items/{variantId}', { params: { path: { cartId, variantId } } }),
  )
}

/**
 * 注文手続きに進む。全品目を引き当て、注文手続きの画面へ移る。
 *
 * ログインしていなければ、ログインの画面へ移し、ログインの後は（カートを移して）カートに戻る。
 * 引き当てられなければ（在庫不足）、カートを表示し直して、足りない品目を示す。
 *
 * 確認していない支払待ちの注文があれば（409）、確認の画面へ移る。確認の画面の「新しく注文する」は、
 * 表示した注文の ID を送る。
 *
 * 支払待ちの注文の上限による拒否（409）なら、上限に達したことを示す確認の画面へ移る。
 *
 * フォームの項目：acknowledgedOrderIds（任意、複数）。
 */
export async function startCheckout(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const acknowledgedOrderIds = formData
    .getAll('acknowledgedOrderIds')
    .map((id) => String(id).trim())
    .filter((id) => id !== '')
  return redirectOnSuccess(async () => {
    if ((await currentCartChoice()).kind !== 'member') {
      await redirectToLogin('/cart')
    }
    const cartId = await memberCartId()
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/carts/{cartId}/checkouts', {
      params: { path: { cartId } },
      ...(acknowledgedOrderIds.length > 0 && { body: { acknowledgedOrderIds } }),
    })
    await redirectIfUnauthenticated(response, '/cart')
    if (!data) {
      const failure = toFailure(response.status, error)
      if (failure.errorCode === PENDING_ORDER_CONFIRMATION_REQUIRED) {
        return PENDING_ORDERS_PATH
      }
      if (failure.errorCode === PENDING_ORDER_LIMIT_EXCEEDED) {
        // 上限に達している。確認の画面で、上限に達したため注文できないことを示す
        return pendingOrdersLimitHref()
      }
      revalidatePath('/cart')
      return failure
    }
    return `/checkout/${data.id}`
  })
}

/**
 * カートを移した結果の知らせを消す（カートの画面で一度示した後）。
 */
export async function dismissCartMergeNotice(): Promise<void> {
  await deleteCartMergeNotice()
}

/**
 * カートの画面で、ログインの直後に移せなかったカートの移す処理を再試行する。
 *
 * カートの画面のクライアントコンポーネントが、表示の後に呼ぶ（サーバーコンポーネントは Cookie を書けないため。
 * 結果は知らせの Cookie でなく戻り値で返し、コンポーネントが表示する）。
 * ログインしていないか、Cookie のカートの ID がなければ、API を呼ばずに none を返す。
 *
 * 移せたときは ID を削除してカートの画面を再表示し、移すカートがないとき（none）は ID だけを削除する。
 * 一時的な失敗は ID を残して failed を返す。ログインが切れていれば、ID を残してログインの画面へ移す
 * （ログインの後はカートの画面に戻る）。
 *
 * Cookie の操作などで例外になったときも、例外にせず failed を返す。
 *
 * @returns 再試行の結果
 */
export async function retryCartMerge(): Promise<CartMergeRetryResult> {
  return withFallback<CartMergeRetryResult>(
    async () => {
      const cartId = await readCartId()
      if (cartId === null || !shouldRetryCartMerge((await currentMember()) !== null, cartId)) {
        return { kind: 'none' }
      }
      const result = await mergeCart(cartId)
      // 移した結果ごとに、Cookie の ID の扱いと、画面に返す値を分ける
      switch (result.kind) {
        case 'merged':
          await deleteCartId()
          revalidatePath('/cart')
          return { kind: 'merged', notice: result.notice }
        case 'gone':
          await deleteCartId()
          return { kind: 'none' }
        case 'failed':
          return { kind: 'failed' }
        case 'unauthenticated':
          return await redirectToLogin('/cart')
      }
    },
    { kind: 'failed' },
  )
}

/** カートの変更の API の呼び出し */
type CartRequest = (
  api: Awaited<ReturnType<typeof getApi>>,
  cartId: string,
) => Promise<{ error?: unknown; response: Response }>

/**
 * カートの変更を行い、成功したらカートの画面を表示し直す。
 * ログインしていれば会員のカート、していなければ Cookie のカート（Cookie の有効期限も延ばす）を変える。
 *
 * @param request API の呼び出し
 */
async function updateCart(request: CartRequest): Promise<ActionResult> {
  return withActionResult(async () => {
    const choice = await currentCartChoice()
    if (choice.kind === 'member') {
      return await updateMemberCart(request)
    }
    if (choice.kind === 'none') {
      return { ok: false, message: translate('cart.errors.missing') } // Cookie のカートがない
    }
    const { error, response } = await request(await getApi(), choice.cartId)
    revalidatePath('/cart')
    if (!response.ok) {
      return toFailure(response.status, error)
    }
    await writeCartId(choice.cartId)
    return SUCCESS
  })
}

/**
 * 会員のカートの変更を、会員の口で行う。401 なら、ログインの画面へ移す。
 *
 * @param request API の呼び出し
 */
async function updateMemberCart(request: CartRequest): Promise<ActionResult> {
  const cartId = await memberCartId()
  const { error, response } = await request(await getMemberApi(), cartId)
  await redirectIfUnauthenticated(response)
  revalidatePath('/cart')
  return response.ok ? SUCCESS : toFailure(response.status, error)
}

/** カートを作り、その ID を返す */
async function createCart(): Promise<string> {
  const api = await getApi()
  const { data, error, response } = await api.POST('/carts')
  if (!data) {
    throw new ApiError(response.status, error as Partial<Problem> | undefined)
  }
  return data.id
}
