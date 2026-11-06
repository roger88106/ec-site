'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useActionState } from 'react'
import { payOrder } from '@/actions/order'
import { ActionError } from '@/components/ActionError'

/**
 * 支払うボタン。決済サービスの支払画面に移る。<br />
 * 文言は呼び出し元の画面の翻訳を受け取る。
 *
 * @param orderId      注文 ID
 * @param label        ボタンの文言
 * @param pendingLabel 移っている間のボタンの文言
 */
export function PayForm({ orderId, label, pendingLabel }: { orderId: string; label: string; pendingLabel: string }) {
  const [result, formAction, pending] = useActionState(payOrder, null)
  return (
    <form action={formAction} className="flex flex-col items-start gap-2">
      <input name="orderId" type="hidden" value={orderId} />
      <Button disabled={pending} type="submit">
        {pending ? pendingLabel : label}
      </Button>
      <ActionError result={result} />
    </form>
  )
}
