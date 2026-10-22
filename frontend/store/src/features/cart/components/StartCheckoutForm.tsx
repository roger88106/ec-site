'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import { useActionState } from 'react'
import { startCheckout } from '@/actions/cart'
import { ActionError } from '@/components/ActionError'

/**
 * 注文手続きに進む。<br />
 * 購入できない品目を含むカートは進めない。在庫が足りず引き当てられなければ、API のメッセージを出す
 * （カートは表示し直され、足りない品目に理由が付く）。
 */
export function StartCheckoutForm({ orderable }: { orderable: boolean }) {
  const t = useTranslations('cart.startCheckout')
  const [result, formAction, pending] = useActionState(startCheckout, null)
  return (
    <form action={formAction} className="space-y-2">
      <Button className="w-full" disabled={!orderable || pending} size="lg" type="submit">
        {pending ? t('reserving') : t('submit') /* 確保中か、進む */}
      </Button>
      {!orderable && <p className="text-muted-foreground text-sm">{t('blocked') /* 進めない理由 */}</p>}
      <ActionError result={result} />
    </form>
  )
}
