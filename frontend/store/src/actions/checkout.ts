'use server'

import type { Route } from 'next'
import { revalidatePath } from 'next/cache'
import { redirect, unstable_rethrow } from 'next/navigation'
import { withActionResult } from '@/actions/withActionResult'
import { getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
import { type ActionResult, toFailure } from '@/lib/actionResult'
import { readInt, readString } from '@/lib/server/formData'

/** 確認した総額が注文の時点と違うときのエラーコード */
const TOTAL_AMOUNT_CHANGED = 'TOTAL_AMOUNT_CHANGED'

/**
 * 注文する。成立したら、決済サービスの支払の画面へ移る（AC-143）。支払の画面の URL を得られなければ、注文の画面へ移る（そこから支払える）。<br />
 * フォームの項目：checkoutId、expectedTotal（画面で表示した総額）、name、postalCode、prefecture、address、phone。
 * 連絡先のメールアドレスは、API が会員の token から取る。
 * 総額が変わっていたら、注文手続きの画面を新しい金額で表示し直す。
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
  const pageUrl = await paymentPageUrl(orderId)
  // redirect は例外で移動を知らせるので、包みの外で呼ぶ。支払の画面は外部の URL
  redirect(pageUrl === null ? `/orders/${orderId}` : (pageUrl as Route))
}

// ============================== プライベート ==============================

/**
 * 注文の支払の画面の URL を求める。得られなければ（決済サービスが応えないなど）null。
 */
async function paymentPageUrl(orderId: string): Promise<string | null> {
  try {
    const api = await getMemberApi()
    const { data } = await api.POST('/orders/{orderId}/payment-page', { params: { path: { orderId } } })
    return data?.url ?? null
  } catch (error) {
    // ログイン画面への移動などは通す（フロントの書き方 第 3.1 節）
    unstable_rethrow(error)
    return null
  }
}
