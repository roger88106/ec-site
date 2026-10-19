'use server'

import type { Route } from 'next'
import { revalidatePath } from 'next/cache'
import { redirectIfUnauthenticated, redirectOnSuccess } from '@/actions/memberAction'
import { withActionResult } from '@/actions/withActionResult'
import { getMemberApi } from '@/api/client'
import type { ErrorCode } from '@/api/types'
import { type ActionResult, toFailure } from '@/lib/actionResult'
import { readInt, readString } from '@/lib/server/formData'

/** 確認した総額が注文の時点と違うときのエラーコード */
const TOTAL_AMOUNT_CHANGED: ErrorCode = 'TOTAL_AMOUNT_CHANGED'

/**
 * 注文する。成立したら、決済サービスの支払の画面へ移る。支払の画面の URL を得られなければ、注文の画面へ移る（そこから支払える）。<br />
 * フォームの項目：checkoutId、expectedTotal（画面で表示した総額）、name、postalCode、prefectureCode、municipalityCode、address、phone。
 * 連絡先のメールアドレスは、API が会員の token から取る。
 * 総額が変わっていたら、注文手続きの画面を新しい金額で表示し直す。
 */
export async function placeOrder(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const checkoutId = readString(formData, 'checkoutId')
  return redirectOnSuccess(async () => {
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/checkouts/{checkoutId}/order', {
      params: { path: { checkoutId } },
      body: {
        shippingAddress: {
          name: readString(formData, 'name'),
          postalCode: readString(formData, 'postalCode'),
          prefectureCode: readString(formData, 'prefectureCode'),
          municipalityCode: readString(formData, 'municipalityCode'),
          address: readString(formData, 'address'),
          phone: readString(formData, 'phone'),
        },
        // 数でなければ API が断る（入力の検証の 400）
        expectedTotal: readInt(formData, 'expectedTotal') ?? Number.NaN,
      },
    })
    await redirectIfUnauthenticated(response)
    if (!data) {
      const failure = toFailure(response.status, error)
      if (failure.errorCode === TOTAL_AMOUNT_CHANGED) {
        revalidatePath(`/checkout/${checkoutId}`)
      }
      return failure
    }
    const pageUrl = await paymentPageUrl(data.id)
    // 支払の画面は外部の URL
    return pageUrl === null ? `/orders/${data.id}` : (pageUrl as Route)
  })
}

// ============================== プライベート ==============================

/**
 * 注文の支払の画面の URL を求める。得られなければ（決済サービスが応えないなど）null。
 */
async function paymentPageUrl(orderId: string): Promise<string | null> {
  const url = await withActionResult(async () => {
    const api = await getMemberApi()
    const { data } = await api.POST('/orders/{orderId}/payment-page', { params: { path: { orderId } } })
    return data?.url ?? null
  })
  // 接続の失敗も、URL を得られなかったときと同じに扱う
  return typeof url === 'string' ? url : null
}
