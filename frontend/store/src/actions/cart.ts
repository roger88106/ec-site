'use server'

import { revalidatePath } from 'next/cache'
import { readCartId, writeCartId } from '@/api/cartCookie'
import { getApi } from '@/api/client'
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

// ============================== プライベート ==============================

/** カートを作り、その ID を返す（AC-73） */
async function createCart(): Promise<string> {
  const api = await getApi()
  const { data, response } = await api.POST('/carts')
  if (!data) {
    throw new Error(`カートを作れませんでした（HTTP ${response.status}）`)
  }
  return data.id
}
