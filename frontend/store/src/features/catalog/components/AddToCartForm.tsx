'use client'

import { Button } from '@ec-site/shared/ui/button'
import { Label } from '@ec-site/shared/ui/label'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import { useActionState, useId } from 'react'
import { addToCart } from '@/actions/cart'
import type { Variant } from '@/api/types'
import { ActionError } from '@/components/ActionError'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { isPurchasable } from '@/features/catalog/variantSelection'
import type { ActionResult } from '@/lib/actionResult'
import { QUANTITY_OPTIONS } from '@/lib/quantity'

/**
 * 数量を選んでカートに入れる。<br />
 * サーバーアクションの状態（結果、送信中か）を持ち、表示は {@link AddToCartFormView} に任せる。
 *
 * @param variant 選んだバリアント（決まっていなければ null）
 */
export function AddToCartForm({ variant }: { variant: Variant | null }) {
  const [result, formAction, pending] = useActionState(addToCart, null)
  return <AddToCartFormView formAction={formAction} pending={pending} result={result} variant={variant} />
}

/**
 * カートに入れるフォームの表示。サーバーアクションの結果を props で受ける。<br />
 * バリアントが決まっていない、または買えないバリアントなら押せない。在庫を超えるなどの拒否は、API のメッセージを出す。
 *
 * @param variant    選んだバリアント（決まっていなければ null）
 * @param result     サーバーアクションの結果（送る前は null）
 * @param formAction フォームを送ったときに呼ぶ関数
 * @param pending    送信中か
 */
export function AddToCartFormView({
  variant,
  result,
  formAction,
  pending,
}: {
  variant: Variant | null
  result: ActionResult | null
  formAction: (formData: FormData) => void
  pending: boolean
}) {
  const t = useTranslations('catalog.addToCart')
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
      {/* 成功も失敗も、常にある領域に入れて確実に読み上げる（エラーメッセージは他の画面と同じ部品で、領域は入れ子にしない） */}
      <div aria-live="polite" className="text-sm">
        {result?.ok === true && (
          <p>
            {t('added') /* 入れた知らせ */}
            <Link className="ml-2 underline" href="/cart">
              {t('viewCart') /* カートを見る */}
            </Link>
          </p>
        )}
        <ActionError inLiveRegion result={result} />
      </div>
    </form>
  )
}
