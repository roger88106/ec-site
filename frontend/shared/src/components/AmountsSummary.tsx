import { formatYen, taxLabel } from '../lib/format'
import { translate } from '../lib/translate'
import { Separator } from '../ui/separator'

/** 金額の内訳の値。各画面の生成した型を import できないので、構造で書く（どちらの画面の型も代入できる）。 */
export type AmountsSummaryValue = {
  itemsTotal: number
  shippingFee: number
  total: number
  taxes: { ratePercent: number; taxableAmount: number; taxAmount: number }[]
}

/** 見た目の種類。`card` は管理画面（右寄せで総額の上に線）、`page` は店舗画面（送料の次に区切り線で総額を大きく）。 */
export type AmountsSummaryVariant = 'card' | 'page'

/**
 * 金額の内訳。商品の合計、送料、総額、税率ごとの消費税額の順に出す。<br />
 * 店舗画面のカート・注文手続き・注文と、管理画面の注文の詳細で同じ部品を使う。<br />
 * hook を使わず、文言は共有の translate で引くので、`"use client"` を付けない。店舗画面のサーバーコンポーネント
 * （async の page）からも、管理画面のブラウザーの描画からも使える。
 */
export function AmountsSummary({ amounts, variant }: { amounts: AmountsSummaryValue; variant: AmountsSummaryVariant }) {
  const isCard = variant === 'card'
  return (
    <dl className={isCard ? 'ml-auto w-full max-w-sm space-y-1 text-sm' : 'space-y-2 text-sm'}>
      <div className="flex justify-between">
        <dt>{translate('common.amounts.itemsTotal') /* 商品の合計 */}</dt>
        <dd className="tabular-nums">{formatYen(amounts.itemsTotal)}</dd>
      </div>
      <div className="flex justify-between">
        <dt>{translate('common.amounts.shippingFee') /* 送料 */}</dt>
        <dd className="tabular-nums">{formatYen(amounts.shippingFee)}</dd>
      </div>
      {!isCard && <Separator />}
      <div
        className={
          isCard ? 'flex justify-between border-t pt-1 font-semibold' : 'flex justify-between font-semibold text-base'
        }
      >
        <dt>{translate('common.amounts.total') /* 総額 */}</dt>
        <dd className="tabular-nums">{formatYen(amounts.total)}</dd>
      </div>
      {amounts.taxes.map((tax) => (
        <div className="text-right text-muted-foreground tabular-nums" key={tax.ratePercent}>
          <dt className="sr-only">
            {translate('common.amounts.taxOf', { ratePercent: tax.ratePercent }) /* 税率ごとの消費税 */}
          </dt>
          <dd>{taxLabel(tax)}</dd>
        </div>
      ))}
    </dl>
  )
}
