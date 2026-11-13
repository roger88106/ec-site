'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useActionState } from 'react'
import { payOrder } from '@/actions/order'
import { ActionError } from '@/components/ActionError'

/**
 * 支払うボタン
 *
 * 決済サービスの支払画面に移る。
 */
export function PayForm({
  orderId,
  label,
  pendingLabel,
}: {
  orderId: string
  /** ボタンの文言 */
  label: string
  /** 移っている間のボタンの文言 */
  pendingLabel: string
}) {
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
