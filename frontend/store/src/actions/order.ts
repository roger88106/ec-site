'use server'

import { revalidatePath } from 'next/cache'
import { unstable_rethrow } from 'next/navigation'
import { getMemberApi } from '@/api/client'
import { redirectToLogin } from '@/auth'
import { type ActionResult, CONNECTION_FAILED, SUCCESS, toFailure } from '@/lib/actionResult'

/**
 * 注文のキャンセルを申請する（AC-95、96）。成功したら、注文の画面を表示し直す。<br />
 * フォームの項目：orderId、reason（任意、500 文字まで）。
 */
export async function requestCancellation(_previous: ActionResult | null, formData: FormData): Promise<ActionResult> {
  const orderId = String(formData.get('orderId') ?? '')
  const reason = String(formData.get('reason') ?? '').trim()
  try {
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
  } catch (error) {
    // ログインの画面への移動（redirect の例外）は、そのまま投げる
    unstable_rethrow(error)
    return { ok: false, message: CONNECTION_FAILED }
  }
}
