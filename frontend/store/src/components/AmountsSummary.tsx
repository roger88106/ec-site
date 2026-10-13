import { Separator } from '@ec-site/shared/ui/separator'
import { useTranslations } from 'next-intl'
import type { Amounts } from '@/api/types'
import { formatYen, taxLabel } from '@/lib/orderDisplay'

/**
 * 金額の内訳（AC-77、85、93。BR-7、8）。商品の合計、送料、総額、税率ごとの消費税額の順に出す。<br />
 * カート・注文手続き・注文で同じ部品を使う。
 */
export function AmountsSummary({ amounts }: { amounts: Amounts }) {
  const t = useTranslations('components.amounts')
  return (
    <dl className="space-y-2 text-sm">
      <div className="flex justify-between">
        <dt>{t('itemsTotal') /* 商品の合計 */}</dt>
        <dd className="tabular-nums">{formatYen(amounts.itemsTotal)}</dd>
      </div>
      <div className="flex justify-between">
        <dt>{t('shippingFee') /* 送料 */}</dt>
        <dd className="tabular-nums">{formatYen(amounts.shippingFee)}</dd>
      </div>
      <Separator />
      <div className="flex justify-between font-semibold text-base">
        <dt>{t('total') /* 総額 */}</dt>
        <dd className="tabular-nums">{formatYen(amounts.total)}</dd>
      </div>
      {amounts.taxes.map((tax) => (
        <div className="text-right text-muted-foreground tabular-nums" key={tax.ratePercent}>
          <dt className="sr-only">{t('taxOf', { ratePercent: tax.ratePercent }) /* 税率ごとの消費税 */}</dt>
          <dd>{taxLabel(tax)}</dd>
        </div>
      ))}
    </dl>
  )
}
