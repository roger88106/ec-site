'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import { useActionState } from 'react'
import { startCheckout } from '@/actions/cart'
import { ActionError } from '@/components/ActionError'

/**
 * 「新しく注文する」。確認の画面で表示した全ての支払待ちの注文の ID を送って、注文手続きを始める。<br />
 * 在庫が足りないときは、API のメッセージを出す。
 */
export function NewOrderForm({ orderIds }: { orderIds: string[] }) {
  const t = useTranslations('checkout.pendingOrders')
  const [result, formAction, pending] = useActionState(startCheckout, null)
  return (
    <form action={formAction} className="flex flex-col items-start gap-2">
      {orderIds.map((orderId) => (
        <input key={orderId} name="acknowledgedOrderIds" type="hidden" value={orderId} />
      ))}
      <Button disabled={pending} type="submit">
        {pending ? t('starting') /* 確保中 */ : t('newOrder') /* 新しく注文する */}
      </Button>
      <ActionError result={result} />
    </form>
  )
}
