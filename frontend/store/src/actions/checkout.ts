'use server'

import { revalidatePath } from 'next/cache'
import { redirect } from 'next/navigation'
import { getApi } from '@/api/client'
import { type ActionResult, CONNECTION_FAILED, toFailure } from '@/lib/actionResult'

/** 確認した総額が注文の時点と違う（AC-89）ときのエラーコード */
const TOTAL_AMOUNT_CHANGED = 'TOTAL_AMOUNT_CHANGED'

/**
 * 注文する（AC-85〜91）。成立したら、注文の画面へ移る。<br />
 * フォームの項目：checkoutId、expectedTotal（画面で表示した総額）、email、name、postalCode、prefecture、address、phone。
 * 総額が変わっていたら、注文手続きの画面を新しい金額で表示し直す（AC-89）。
 */
export async function placeOrder(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const checkoutId = text(formData, 'checkoutId')
  let orderId: string
  try {
    const api = await getApi()
    const { data, error, response } = await api.POST('/checkouts/{checkoutId}/order', {
      params: { path: { checkoutId } },
      body: {
        email: text(formData, 'email'),
        shippingAddress: {
          name: text(formData, 'name'),
          postalCode: text(formData, 'postalCode'),
          prefecture: text(formData, 'prefecture'),
          address: text(formData, 'address'),
          phone: text(formData, 'phone'),
        },
        expectedTotal: Number(formData.get('expectedTotal')),
      },
    })
    if (!data) {
      const failure = toFailure(response.status, error)
      if (failure.errorCode === TOTAL_AMOUNT_CHANGED) {
        revalidatePath(`/checkout/${checkoutId}`)
      }
      return failure
    }
    orderId = data.id
  } catch {
    return { ok: false, message: CONNECTION_FAILED }
  }
  // redirect は例外で移動を知らせるので、try の外で呼ぶ
  redirect(`/orders/${orderId}`)
}

// ============================== プライベート ==============================

/** フォームの項目の文字（前後の空白を除く） */
function text(formData: FormData, name: string): string {
  return String(formData.get(name) ?? '').trim()
}
