'use client'

import { Badge } from '@ec-site/shared/ui/badge'
import { Button } from '@ec-site/shared/ui/button'
import { useTranslations } from 'next-intl'
import type { Variant, VariantAxis } from '@/api/types'
import {
  isPurchasable,
  isValueSelectable,
  type Selection,
  stockLabel,
  toggleValue,
} from '@/features/catalog/variantSelection'

/**
 * バリアントの選択と在庫状況。<br />
 * 選んだ値（selection）と選んだバリアントは親が持ち、この部品は受け取って出し、値を押したときに新しい選択を知らせる。
 * 軸ごとに値を並べ、在庫切れ・販売停止のバリアントにしかならない値は、選べない状態（グレー）で出す。
 * すべての軸を選ぶと、そのバリアントの在庫状況（「在庫あり」「在庫なし」）を出す。
 */
export function VariantPicker({
  axes,
  variants,
  selection,
  chosen,
  onChange,
}: {
  axes: VariantAxis[]
  variants: Variant[]
  /** 軸ごとに選んだ値 */
  selection: Selection
  /** 選んだ選択で決まるバリアント。決まっていなければ null */
  chosen: Variant | null
  /** 値を押して、選択が変わったときに、新しい選択を渡して呼ぶ */
  onChange: (selection: Selection) => void
}) {
  const tStock = useTranslations('catalog.stock')
  const t = useTranslations('catalog.variantPicker')
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
              // 選べない値は取り消し（s）で出す（取り消し線は s の既定の見た目）。
              // 選んでいる値は、選べなくなっても外せるよう押せるままにする
              return (
                <Button
                  aria-pressed={selected}
                  disabled={!selectable && !selected}
                  key={value}
                  onClick={() => onChange(toggleValue(selection, axisIndex, value))}
                  size="sm"
                  type="button"
                  variant={selected ? 'default' : 'outline'}
                >
                  {selectable ? value : <s>{value}</s>}
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
