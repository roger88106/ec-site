'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import { useActionState } from 'react'
import { payOrder } from '@/actions/order'
import { ActionError } from '@/components/ActionError'

/** 確認の画面の「支払へ進む」。注文の画面の「支払う」と同じ `payOrder` で、決済サービスの支払画面に移る */
export function PayPendingOrderForm({ orderId }: { orderId: string }) {
  const t = useTranslations('checkout.pendingOrders')
  const [result, formAction, pending] = useActionState(payOrder, null)
  return (
    <form action={formAction} className="flex flex-col items-start gap-2">
      <input name="orderId" type="hidden" value={orderId} />
      <Button disabled={pending} type="submit">
        {pending ? t('paying') /* 移っています */ : t('pay') /* 支払へ進む */}
      </Button>
      <ActionError result={result} />
    </form>
  )
}
