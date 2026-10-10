'use client'

import { useActionState, useId } from 'react'
import { changeCartItemQuantity, removeCartItem } from '@/actions/cart'
import type { QuoteItem } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { formatYen } from '@/lib/orderDisplay'

/** 1 品目の数量の選択肢（AC-75） */
const QUANTITIES = Array.from({ length: 10 }, (_, index) => index + 1)

/**
 * カートの品目の数量の変更と削除（AC-78）、小計。<br />
 * 数量は選んだときに送る。在庫を超えるなどの拒否は、API のメッセージを品目の下に出す。
 */
export function CartItemControls({ item }: { item: QuoteItem }) {
  const [changeResult, changeAction, changing] = useActionState(changeCartItemQuantity, null)
  const [removeResult, removeAction, removing] = useActionState(removeCartItem, null)
  const quantityId = useId()
  const failure = [changeResult, removeResult].find((result) => result?.ok === false)

  return (
    <div className="flex flex-col items-end gap-2 text-sm">
      <form action={changeAction} className="flex items-center gap-2">
        <input name="variantId" type="hidden" value={item.variantId} />
        <Label htmlFor={quantityId}>数量</Label>
        <NativeSelect
          className="w-20"
          defaultValue={String(item.quantity)}
          disabled={changing || removing}
          id={quantityId}
          // 送信の後に表示し直した数量を初期値にするため、数量が変わったら作り直す
          key={item.quantity}
          name="quantity"
          onChange={(event) => event.currentTarget.form?.requestSubmit()}
        >
          {QUANTITIES.map((quantity) => (
            <NativeSelectOption key={quantity} value={quantity}>
              {quantity}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </form>
      <p className="font-semibold tabular-nums">{formatYen(item.subtotal)}</p>
      <form action={removeAction}>
        <input name="variantId" type="hidden" value={item.variantId} />
        <Button disabled={changing || removing} size="sm" type="submit" variant="outline">
          {removing ? '削除しています…' : '削除'}
        </Button>
      </form>
      {failure?.ok === false && (
        <p aria-live="polite" className="text-destructive">
          {failure.message}
        </p>
      )}
    </div>
  )
}
