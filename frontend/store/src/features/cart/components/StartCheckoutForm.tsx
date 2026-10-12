'use client'

import { Button } from '@ec-site/shared/ui/button'
import { useActionState } from 'react'
import { startCheckout } from '@/actions/cart'

/**
 * 注文手続きに進む（AC-82、88）。<br />
 * 購入できない品目を含むカートは進めない。在庫が足りず引き当てられなければ、API のメッセージを出す
 * （カートは表示し直され、足りない品目に理由が付く）。
 */
export function StartCheckoutForm({ orderable }: { orderable: boolean }) {
  const [result, formAction, pending] = useActionState(startCheckout, null)
  return (
    <form action={formAction} className="space-y-2">
      <Button className="w-full" disabled={!orderable || pending} size="lg" type="submit">
        {pending ? '在庫を確保しています…' : '注文手続きへ進む'}
      </Button>
      {!orderable && (
        <p className="text-muted-foreground text-sm">購入できない商品をカートから削除すると、注文手続きに進めます。</p>
      )}
      {result?.ok === false && (
        <p aria-live="polite" className="text-destructive text-sm">
          {result.message}
        </p>
      )}
    </form>
  )
}
