import { axisValuesLabel, formatYen } from '@ec-site/shared/lib/format'
import { cn } from 'cn'
import { useTranslations } from 'next-intl'
import type { Order } from '@/api/types'
import { PriceText } from '@/components/PriceText'

/**
 * 注文の明細の一覧
 *
 * 明細ごとに商品名・軸の値・単価・数量・小計を示す。余白と角の丸みは、呼ぶ画面に合わせて受ける。
 */
export function OrderLineList({
  lines,
  className,
  itemClassName,
  quantityLabel,
}: {
  /** 注文の明細 */
  lines: Order['lines']
  /** 一覧の要素に付ける class（角の丸みなど） */
  className: string
  /** 明細の要素に付ける class（余白など） */
  itemClassName: string
  /** 数量の文言を返す関数 */
  quantityLabel: (quantity: number) => string
}) {
  const tLabels = useTranslations('labels')
  return (
    <ul className={cn('divide-y border', className)}>
      {lines.map((line) => (
        <li className={cn('flex justify-between gap-4 text-sm', itemClassName)} key={line.variantCode}>
          <div className="space-y-1">
            <p className="font-medium">{line.productName}</p>
            {line.axisValues.length > 0 && (
              <p className="text-muted-foreground">
                {axisValuesLabel(line.axisValues, tLabels('axisSeparator') /* 軸の区切り */)}
              </p>
            )}
            <PriceText price={line.unitPrice} />
          </div>
          <div className="text-right">
            <p>{quantityLabel(line.quantity)}</p>
            <p className="font-semibold tabular-nums">{formatYen(line.subtotal)}</p>
          </div>
        </li>
      ))}
    </ul>
  )
}
