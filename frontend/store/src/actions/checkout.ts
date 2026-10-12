'use server'

import { revalidatePath } from 'next/cache'
import { redirect } from 'next/navigation'
import { withActionResult } from '@/actions/withActionResult'
import { getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
import { type ActionResult, toFailure } from '@/lib/actionResult'
import { readInt, readString } from '@/lib/server/formData'

/** 確認した総額が注文の時点と違う（AC-89）ときのエラーコード */
const TOTAL_AMOUNT_CHANGED = 'TOTAL_AMOUNT_CHANGED'

/**
 * 注文する（AC-85〜91）。成立したら、注文の画面へ移る。<br />
 * フォームの項目：checkoutId、expectedTotal（画面で表示した総額）、name、postalCode、prefecture、address、phone。
 * 連絡先のメールアドレスは、API が会員の token から取る（AC-127）。
 * 総額が変わっていたら、注文手続きの画面を新しい金額で表示し直す（AC-89）。
 */
export async function placeOrder(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const checkoutId = readString(formData, 'checkoutId')
  const orderId = await withActionResult(async () => {
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/checkouts/{checkoutId}/order', {
      params: { path: { checkoutId } },
      body: {
        shippingAddress: {
          name: readString(formData, 'name'),
          postalCode: readString(formData, 'postalCode'),
          prefecture: readString(formData, 'prefecture'),
          address: readString(formData, 'address'),
          phone: readString(formData, 'phone'),
        },
        // 数でなければ API が断る（入力の検証の 400）
        expectedTotal: readInt(formData, 'expectedTotal') ?? Number.NaN,
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
    return data.id
  })
  if (typeof orderId !== 'string') {
    return orderId
  }
  // redirect は例外で移動を知らせるので、包みの外で呼ぶ
  redirect(`/orders/${orderId}`)
}

// ============================== プライベート ==============================
