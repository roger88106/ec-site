import type { Price } from '@/api/types'
import { yenParts } from '@/lib/price'

/** 価格（BR-4）。税込価格を大きく、税抜価格を括弧で小さく出す（文字は formatPrice と同じ） */
export function PriceText({ price, size = 'md' }: { price: Price; size?: 'md' | 'lg' }) {
  const { inclTax, exclTax } = yenParts(price)
  return (
    <span className="tabular-nums">
      <span className={size === 'lg' ? 'font-semibold text-2xl' : 'font-semibold'}>{inclTax} 円</span>
      <span className="text-muted-foreground text-sm">（税抜 {exclTax} 円）</span>
    </span>
  )
}
