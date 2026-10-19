'use server'

import type { Route } from 'next'
import { revalidatePath } from 'next/cache'
import { redirectIfUnauthenticated, redirectOnSuccess } from '@/actions/memberAction'
import { withActionResult } from '@/actions/withActionResult'
import { getMemberApi } from '@/api/client'
import { type ActionResult, SUCCESS, toFailure } from '@/lib/actionResult'
import { readString } from '@/lib/server/formData'

/**
 * 注文のキャンセルを申請する。成功したら、注文の画面を表示し直す。<br />
 * フォームの項目：orderId、reason（任意、500 文字まで）。
 */
export async function requestCancellation(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const orderId = readString(formData, 'orderId')
  const reason = readString(formData, 'reason')
  return withActionResult(async () => {
    const api = await getMemberApi()
    const { error, response } = await api.PUT('/orders/{orderId}/cancellation-request', {
      params: { path: { orderId } },
      body: { reason: reason === '' ? null : reason },
    })
    await redirectIfUnauthenticated(response)
    revalidatePath(`/orders/${orderId}`)
    return response.ok ? SUCCESS : toFailure(response.status, error)
  })
}

/**
 * 支払う：決済サービスの支払の画面に移る。<br />
 * 支払えなければ（支払済み、期限の後）API のメッセージを返し、注文の画面を表示し直す。
 * フォームの項目：orderId。
 */
export async function payOrder(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const orderId = readString(formData, 'orderId')
  return redirectOnSuccess(async () => {
    const api = await getMemberApi()
    const { data, error, response } = await api.POST('/orders/{orderId}/payment-page', {
      params: { path: { orderId } },
    })
    await redirectIfUnauthenticated(response)
    if (!data) {
      revalidatePath(`/orders/${orderId}`)
      return toFailure(response.status, error)
    }
    // 決済サービスの画面（外部の URL）
    return data.url as Route
  })
}
