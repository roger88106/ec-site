'use server'

import { revalidatePath } from 'next/cache'
import { withActionResult } from '@/actions/withActionResult'
import { getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
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
    if (response.status === 401) {
      await redirectToLogin()
    }
    revalidatePath(`/orders/${orderId}`)
    return response.ok ? SUCCESS : toFailure(response.status, error)
  })
}

/**
 * 支払う：決済サービスの支払の画面に移る（AC-151）。T7505 で実装する。<br />
 * フォームの項目：orderId。
 */
export async function payOrder(_previous: ActionResult | null, _formData: FormData): Promise<ActionResult> {
  return { ok: false, message: 'まだ支払えません' }
}
