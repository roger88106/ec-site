'use client'

import { Button } from '@ec-site/shared/ui/button'
import { Label } from '@ec-site/shared/ui/label'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import { useActionState, useId } from 'react'
import { addToCart } from '@/actions/cart'
import type { Variant } from '@/api/types'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { isPurchasable } from '@/features/catalog/variantSelection'
import { QUANTITY_OPTIONS } from '@/lib/orderDisplay'

/**
 * 数量を選んでカートに入れる。<br />
 * バリアントが決まっていない、または買えないバリアントなら押せない。在庫を超えるなどの拒否は、API のメッセージを出す。
 */
export function AddToCartForm({ variant }: { variant: Variant | null }) {
  const t = useTranslations('catalog.addToCart')
  const [result, formAction, pending] = useActionState(addToCart, null)
  const selectable = variant !== null && isPurchasable(variant)
  const quantityId = useId()

  return (
    <form action={formAction} className="space-y-3">
      <input name="variantId" type="hidden" value={variant?.id ?? ''} />
      <div className="flex items-center gap-3">
        <Label htmlFor={quantityId}>{t('quantity') /* 数量 */}</Label>
        <NativeSelect className="w-20" defaultValue="1" id={quantityId} name="quantity">
          {QUANTITY_OPTIONS.map((quantity) => (
            <NativeSelectOption key={quantity} value={quantity}>
              {quantity}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        <Button disabled={!selectable || pending} type="submit">
          {pending ? t('adding') : t('submit') /* 送信中か、カートに入れる */}
        </Button>
      </div>
      <div aria-live="polite" className="text-sm">
        {result?.ok === true && (
          <p>
            {t('added') /* 入れた知らせ */}
            <Link className="ml-2 underline" href="/cart">
              {t('viewCart') /* カートを見る */}
            </Link>
          </p>
        )}
        {result?.ok === false && <p className="text-destructive">{result.message}</p>}
      </div>
    </form>
  )
}
