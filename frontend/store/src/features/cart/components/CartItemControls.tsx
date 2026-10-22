'use client'

import { formatYen } from '@ec-site/shared/lib/format'
import { Button } from '@ec-site/shared/ui/button'
import { Label } from '@ec-site/shared/ui/label'
import { useTranslations } from 'next-intl'
import { useActionState, useId } from 'react'
import { changeCartItemQuantity, removeCartItem } from '@/actions/cart'
import type { QuoteItem } from '@/api/types'
import { ActionError } from '@/components/ActionError'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { QUANTITY_OPTIONS } from '@/lib/quantity'

/**
 * カートの品目の数量の変更と削除、小計（購入できない品目は打ち消し線）。<br />
 * 数量は選んだときに送る。在庫を超えるなどの拒否は、API のメッセージを品目の下に出す。
 */
export function CartItemControls({ item }: { item: QuoteItem }) {
  const t = useTranslations('cart.controls')
  const [changeResult, changeAction, changing] = useActionState(changeCartItemQuantity, null)
  const [removeResult, removeAction, removing] = useActionState(removeCartItem, null)
  const quantityId = useId()
  const failure = [changeResult, removeResult].find((result) => result?.ok === false)

  return (
    <div className="flex flex-col items-end gap-2 text-sm">
      <form action={changeAction} className="flex items-center gap-2">
        <input name="variantId" type="hidden" value={item.variantId} />
        <Label htmlFor={quantityId}>{t('quantity') /* 数量 */}</Label>
        <NativeSelect
          className="w-20"
          defaultValue={String(item.quantity)}
          disabled={changing || removing}
          id={quantityId}
          // 送信の後に表示し直した数量を初期値にするため、数量が変わったら再作成する
          key={item.quantity}
          name="quantity"
          onChange={(event) => event.currentTarget.form?.requestSubmit()}
        >
          {QUANTITY_OPTIONS.map((quantity) => (
            <NativeSelectOption key={quantity} value={quantity}>
              {quantity}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </form>
      {/* 購入できない品目は合計に含めないので、小計を取り消し（s）で出す。取り消し線は s の既定の見た目 */}
      <p className={item.purchasable ? 'font-semibold tabular-nums' : 'text-muted-foreground tabular-nums'}>
        {item.purchasable ? formatYen(item.subtotal) : <s>{formatYen(item.subtotal)}</s>}
      </p>
      <form action={removeAction}>
        <input name="variantId" type="hidden" value={item.variantId} />
        <Button disabled={changing || removing} size="sm" type="submit" variant="outline">
          {removing ? t('removing') : t('remove') /* 削除中か、削除 */}
        </Button>
      </form>
      <ActionError message={failure?.ok === false ? failure.message : null} />
    </div>
  )
}
