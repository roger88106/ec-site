'use client'

import { useState } from 'react'
import type { Variant, VariantAxis } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
export function VariantPicker({ axes, variants }: { axes: VariantAxis[]; variants: Variant[] }) {
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
              return (
                <Button
                  aria-pressed={selected}
                  className={selectable ? undefined : 'line-through'}
                  disabled={!selectable && !selected}
                  key={value}
                  onClick={() => setSelection(toggleValue(selection, axisIndex, value))}
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
          <Badge variant="secondary">在庫なし</Badge>
        ) : chosen ? (
          <Badge variant={isPurchasable(chosen) ? 'default' : 'secondary'}>{stockLabel(chosen)}</Badge>
        ) : (
          <span className="text-muted-foreground">{missingAxes.join('・')}を選ぶと、在庫状況を表示します。</span>
        )}
      </div>
    </div>
  )
}
