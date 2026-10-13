import { useTranslations } from 'next-intl'
import type { Price } from '@/api/types'
import { yenParts } from '@/lib/price'

/** 価格（BR-4）。税込価格を大きく、税抜価格を括弧で小さく出す（文字は formatPrice と同じ） */
export function PriceText({ price, size = 'md' }: { price: Price; size?: 'md' | 'lg' }) {
  const t = useTranslations('components.priceText')
  const { inclTax, exclTax } = yenParts(price)
  return (
    <span className="tabular-nums">
      <span className={size === 'lg' ? 'font-semibold text-2xl' : 'font-semibold'}>
        {t('inclTax', { amount: inclTax }) /* 税込の金額 */}
      </span>
      <span className="text-muted-foreground text-sm">{t('exclTax', { amount: exclTax }) /* 税抜の金額 */}</span>
    </span>
  )
}
