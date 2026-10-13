'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import { useActionState } from 'react'
import { payOrder } from '@/actions/order'

/** 「支払う」。決済サービスの支払の画面に移る（AC-151） */
export function PayForm({ orderId }: { orderId: string }) {
  const t = useTranslations('order.payment')
  const [result, formAction, pending] = useActionState(payOrder, null)
  return (
    <form action={formAction} className="space-y-2">
      <input name="orderId" type="hidden" value={orderId} />
      <Button disabled={pending} type="submit">
        {pending ? t('paying') /* 移っています */ : t('pay') /* 支払う */}
      </Button>
      {result?.ok === false && (
        <p aria-live="polite" className="text-destructive text-sm">
          {result.message}
        </p>
      )}
    </form>
  )
}
