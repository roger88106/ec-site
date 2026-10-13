'use client'

import { Badge } from '@ec-site/shared/ui/badge'
import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import type { Variant, VariantAxis } from '@/api/types'
import {
  emptySelection,
  isPurchasable,
  isValueSelectable,
  selectedVariant,
  stockLabel,
  toggleValue,
} from '@/features/catalog/variantSelection'

/**
 * バリアントの選択と在庫状況（AC-62〜64、28）。<br />
 * 軸ごとに値を並べ、在庫切れ・販売停止のバリアントにしかならない値は、選べない状態（グレー）で出す。
 * すべての軸を選ぶと、そのバリアントの在庫状況（「在庫あり」「在庫なし」）を出す。
 */
export function VariantPicker({
  axes,
  variants,
  onSelectVariant,
}: {
  axes: VariantAxis[]
  variants: Variant[]
  /** すべての軸を選んでバリアントが決まったとき（決まらなくなったときは null）に呼ぶ */
  onSelectVariant?: (variant: Variant | null) => void
}) {
  const tStock = useTranslations('catalog.stock')
  const t = useTranslations('catalog.variantPicker')
  const [selection, setSelection] = useState(() => emptySelection(axes))
  const chosen = selectedVariant(axes, variants, selection)
  const soldOut = !variants.some(isPurchasable)
  const missingAxes = axes.filter((_, index) => selection[index] === null).map((axis) => axis.name)

  return (
    <div className="space-y-4">
      {axes.map((axis, axisIndex) => (
        <fieldset className="space-y-2" key={axis.name}>
          <legend className="font-medium text-sm">
            {axis.name}
            {selection[axisIndex] && <span className="ml-2 text-muted-foreground">{selection[axisIndex]}</span>}
          </legend>
          <div className="flex flex-wrap gap-2">
            {axis.values.map((value) => {
              const selected = selection[axisIndex] === value
              const selectable = isValueSelectable(axes, variants, selection, axisIndex, value)
              // 選べない値は取り消し線で出す。選んでいる値は、選べなくなっても外せるよう押せるままにする
              return (
                <Button
                  aria-pressed={selected}
                  className={selectable ? undefined : 'line-through'}
                  disabled={!selectable && !selected}
                  key={value}
                  onClick={() => {
                    const next = toggleValue(selection, axisIndex, value)
                    setSelection(next)
                    onSelectVariant?.(selectedVariant(axes, variants, next) ?? null)
                  }}
                  size="sm"
                  type="button"
                  variant={selected ? 'default' : 'outline'}
                >
                  {value}
                </Button>
              )
            })}
          </div>
        </fieldset>
      ))}
      <div aria-live="polite" className="text-sm">
        {soldOut ? (
          <Badge variant="secondary">{tStock('unavailable') /* 在庫なし */}</Badge>
        ) : chosen ? (
          <Badge variant={isPurchasable(chosen) ? 'default' : 'secondary'}>{stockLabel(chosen)}</Badge>
        ) : (
          <span className="text-muted-foreground">
            {t('chooseAxes', { axes: missingAxes.join(t('axisSeparator')) }) /* 選んでいない軸 */}
          </span>
        )}
      </div>
    </div>
  )
}
