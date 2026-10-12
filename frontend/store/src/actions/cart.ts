'use server'

import { revalidatePath } from 'next/cache'
import { redirect, unstable_rethrow } from 'next/navigation'
import { readCartId, writeCartId } from '@/api/cartCookie'
import { getApi, getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
import { type ActionResult, CONNECTION_FAILED, SUCCESS, toFailure } from '@/lib/actionResult'
import { CART_NOT_FOUND, withCart } from '@/lib/cartId'

/**
 * カートに入れる（AC-72〜76）。カートがなければ作り、Cookie に書く（AC-73）。<br />
 * フォームの項目：variantId、quantity。
 */
export async function addToCart(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const variantId = String(formData.get('variantId') ?? '')
  const quantity = Number(formData.get('quantity'))
  try {
    const api = await getApi()
    const { cartId, value } = await withCart(await readCartId(), createCart, async (id) => {
      const result = await api.POST('/carts/{cartId}/items', {
        params: { path: { cartId: id } },
        body: { variantId, quantity },
      })
      return result.response.status === 404 ? CART_NOT_FOUND : result
    })
    // 作り直したときは新しい ID を、そうでなくても有効期限を延ばすために書く
    await writeCartId(cartId)
    if (!value.response.ok) {
      return toFailure(value.response.status, value.error)
    }
    revalidatePath('/cart')
    return SUCCESS
  } catch {
    return { ok: false, message: CONNECTION_FAILED }
  }
}

/**
 * 品目の数量を変える（AC-75、76、78）。フォームの項目：variantId、quantity。
 */
export async function changeCartItemQuantity(
  _previous: ActionResult | null,
  formData: FormData,
): Promise<ActionResult> {
  const variantId = String(formData.get('variantId') ?? '')
  const quantity = Number(formData.get('quantity'))
  return updateCart(async (api, cartId) =>
    api.PUT('/carts/{cartId}/items/{variantId}', { params: { path: { cartId, variantId } }, body: { quantity } }),
  )
}

/**
 * 品目を削除する（AC-78）。フォームの項目：variantId。
 */
export async function removeCartItem(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const variantId = String(formData.get('variantId') ?? '')
  return updateCart(async (api, cartId) =>
    api.DELETE('/carts/{cartId}/items/{variantId}', { params: { path: { cartId, variantId } } }),
  )
}

/**
 * 注文手続きに進む（AC-82〜84）。全品目を引き当て、注文手続きの画面へ移る。<br />
 * 引き当てられなければ（在庫不足）、カートを表示し直して、足りない品目を示す（AC-82）。
 */
export async function startCheckout(_previous: ActionResult | null, _formData: FormData): Promise<ActionResult> {
  const cartId = await readCartId()
  if (cartId === null) {
    return { ok: false, message: CART_MISSING }
  }
  let checkoutId: string
  try {
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/carts/{cartId}/checkouts', {
      params: { path: { cartId } },
    })
    if (response.status === 401) {
      // 注文手続きに進むにはログインが要る。ログインの後はカートに戻る（AC-125）
      await redirectToLogin('/cart')
    }
    if (!data) {
      revalidatePath('/cart')
      return toFailure(response.status, error)
    }
    checkoutId = data.id
  } catch (error) {
    // ログインの画面への移動（redirect の例外）は、そのまま投げる
    unstable_rethrow(error)
    return { ok: false, message: CONNECTION_FAILED }
  }
  // redirect は例外で移動を知らせるので、try の外で呼ぶ
  redirect(`/checkout/${checkoutId}`)
}

// ============================== プライベート ==============================

/** Cookie のカートがないときの案内 */
const CART_MISSING = 'カートが見つかりません。商品をカートに入れ直してください。'

/**
 * カートの変更を行い、成功したらカートの画面を表示し直す。Cookie の有効期限も延ばす。
 *
 * @param request API の呼び出し
 */
async function updateCart(
  request: (
    api: Awaited<ReturnType<typeof getApi>>,
    cartId: string,
  ) => Promise<{ error?: unknown; response: Response }>,
): Promise<ActionResult> {
  const cartId = await readCartId()
  if (cartId === null) {
    return { ok: false, message: CART_MISSING }
  }
  try {
    const { error, response } = await request(await getApi(), cartId)
    revalidatePath('/cart')
    if (!response.ok) {
      return toFailure(response.status, error)
    }
    await writeCartId(cartId)
    return SUCCESS
  } catch {
    return { ok: false, message: CONNECTION_FAILED }
  }
}

/** カートを作り、その ID を返す（AC-73） */
async function createCart(): Promise<string> {
  const api = await getApi()
  const { data, response } = await api.POST('/carts')
  if (!data) {
    throw new Error(`カートを作れませんでした（HTTP ${response.status}）`)
  }
  return data.id
}
