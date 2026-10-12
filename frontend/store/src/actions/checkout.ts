'use server'

import { revalidatePath } from 'next/cache'
import { redirect, unstable_rethrow } from 'next/navigation'
import { getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
import { type ActionResult, CONNECTION_FAILED, toFailure } from '@/lib/actionResult'

/** 確認した総額が注文の時点と違う（AC-89）ときのエラーコード */
const TOTAL_AMOUNT_CHANGED = 'TOTAL_AMOUNT_CHANGED'

/**
 * 注文する（AC-85〜91）。成立したら、注文の画面へ移る。<br />
 * フォームの項目：checkoutId、expectedTotal（画面で表示した総額）、name、postalCode、prefecture、address、phone。
 * 連絡先のメールアドレスは、API が会員の token から取る（AC-127）。
 * 総額が変わっていたら、注文手続きの画面を新しい金額で表示し直す（AC-89）。
 */
export async function placeOrder(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const checkoutId = text(formData, 'checkoutId')
  let orderId: string
  try {
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/checkouts/{checkoutId}/order', {
      params: { path: { checkoutId } },
      body: {
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
    if (response.status === 401) {
      await redirectToLogin()
    }
    if (!data) {
      const failure = toFailure(response.status, error)
      if (failure.errorCode === TOTAL_AMOUNT_CHANGED) {
        revalidatePath(`/checkout/${checkoutId}`)
      }
      return failure
    }
    orderId = data.id
  } catch (error) {
    // ログインの画面への移動（redirect の例外）は、そのまま投げる
    unstable_rethrow(error)
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
