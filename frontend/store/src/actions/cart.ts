'use server'

import { revalidatePath } from 'next/cache'
import { redirectIfUnauthenticated, redirectOnSuccess } from '@/actions/memberAction'
import { withActionResult } from '@/actions/withActionResult'
import { currentCartChoice, memberCartId } from '@/api/cart'
import { deleteCartMergeNotice, writeCartId } from '@/api/cartCookie'
import { getApi, getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
import { translate } from '@/i18n/translate'
import { type ActionResult, SUCCESS, toFailure } from '@/lib/actionResult'
import { CART_NOT_FOUND, withCart } from '@/lib/cartId'
import { readInt, readString } from '@/lib/server/formData'

/**
 * カートに入れる。<br />
 * ログインしていれば会員のカートに入れる。していなければ Cookie のカートに入れ、カートがなければ作って
 * Cookie に書く。フォームの項目：variantId、quantity。
 */
export async function addToCart(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const variantId = readString(formData, 'variantId')
  // 数でなければ API が断る（入力の検証の 400）
  const quantity = readInt(formData, 'quantity') ?? Number.NaN
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
  // 数でなければ API が断る（入力の検証の 400）
  const quantity = readInt(formData, 'quantity') ?? Number.NaN
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
 * 注文手続きに進む。全品目を引き当て、注文手続きの画面へ移る。<br />
 * ログインしていなければ、ログインの画面へ移し、ログインの後は（カートを移して）カートに戻る。
 * 引き当てられなければ（在庫不足）、カートを表示し直して、足りない品目を示す。
 */
export async function startCheckout(_previous: ActionResult | null, _formData: FormData): Promise<ActionResult> {
  return redirectOnSuccess(async () => {
    if ((await currentCartChoice()).kind !== 'member') {
      await redirectToLogin('/cart')
    }
    const cartId = await memberCartId()
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/carts/{cartId}/checkouts', {
      params: { path: { cartId } },
    })
    await redirectIfUnauthenticated(response, '/cart')
    if (!data) {
      revalidatePath('/cart')
      return toFailure(response.status, error)
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

// ============================== プライベート ==============================

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
  const { data, response } = await api.POST('/carts')
  if (!data) {
    throw new Error(`カートを作れませんでした（HTTP ${response.status}）`)
  }
  return data.id
}
