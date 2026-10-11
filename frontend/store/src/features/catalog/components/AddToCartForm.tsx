'use client'

import Link from 'next/link'
import { useActionState, useId } from 'react'
import { addToCart } from '@/actions/cart'
import type { Variant } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { isPurchasable } from '@/features/catalog/variantSelection'
import { QUANTITY_OPTIONS } from '@/lib/orderDisplay'

/**
 * 数量を選んでカートに入れる（AC-72、74〜76）。<br />
 * バリアントが決まっていない、または買えないバリアントなら押せない。在庫を超えるなどの拒否は、API のメッセージを出す。
 */
export function AddToCartForm({ variant }: { variant: Variant | null }) {
  const [result, formAction, pending] = useActionState(addToCart, null)
  const selectable = variant !== null && isPurchasable(variant)
  const quantityId = useId()

  return (
    <form action={formAction} className="space-y-3">
      <input name="variantId" type="hidden" value={variant?.id ?? ''} />
      <div className="flex items-center gap-3">
        <Label htmlFor={quantityId}>数量</Label>
        <NativeSelect className="w-20" defaultValue="1" id={quantityId} name="quantity">
          {QUANTITY_OPTIONS.map((quantity) => (
            <NativeSelectOption key={quantity} value={quantity}>
              {quantity}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        <Button disabled={!selectable || pending} type="submit">
          {pending ? '入れています…' : 'カートに入れる'}
        </Button>
      </div>
      <div aria-live="polite" className="text-sm">
        {result?.ok === true && (
          <p>
            カートに入れました。
            <Link className="ml-2 underline" href="/cart">
              カートを見る
            </Link>
          </p>
        )}
        {result?.ok === false && <p className="text-destructive">{result.message}</p>}
      </div>
    </form>
  )
}
