import { LOCALE, TIME_ZONE } from './locale'
import { translate } from './translate'

/** 既定の数の書式（3 桁ごとのカンマ）。金額の整形も使う */
const number = new Intl.NumberFormat(LOCALE)

/** 数（例：「1,234」）。3 桁ごとに区切り、単位は付けない */
export function formatNumber(value: number): string {
  return number.format(value)
}

/** 日時の書式（日本時間の年・月・日・時・分をゼロ埋めで出す。例：「2026/10/10 14:05」）。管理画面の日時の入力欄も使う */
export const japanDateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** 金額（例：「1,000 円」） */
export function formatYen(amount: number): string {
  return translate('common.yen', { amount: formatNumber(amount) })
}

/**
 * 価格の表示。税込価格を主に、税抜価格を括弧で併記する。<br />
 * 例：「1,980 円（税抜 1,800 円）」。有効な税率がなく税込価格がなければ、税抜価格だけを出す。
 */
export function formatPrice(price: { inclTax?: number | null; exclTax: number }): string {
  if (price.inclTax == null) {
    return translate('common.priceExclTaxOnly', { exclTax: formatNumber(price.exclTax) })
  }
  return translate('common.priceWithExclTax', {
    inclTax: formatNumber(price.inclTax),
    exclTax: formatNumber(price.exclTax),
  })
}

/** 日時の表示。日本時間の「yyyy/MM/dd HH:mm」。空白（null）は空文字 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  return japanDateTimeFormat.format(new Date(iso))
}

/** 1 つの税率の消費税額（例：「10% 対象 11,000 円（内消費税 1,000 円）」） */
export function taxLabel(tax: { ratePercent: number; taxableAmount: number; taxAmount: number }): string {
  return translate('common.taxAmount', {
    ratePercent: tax.ratePercent,
    taxableAmount: formatYen(tax.taxableAmount),
    taxAmount: formatYen(tax.taxAmount),
  })
}

/** 郵便番号（API はハイフンなしの 7 桁を返す。例：「100-0001」） */
export function formatPostalCode(postalCode: string): string {
  return `${postalCode.slice(0, 3)}-${postalCode.slice(3)}`
}

/**
 * 軸の名前と値を並べる（例：「色：黒　サイズ：M」）。
 *
 * @param axisValues 軸の名前と値
 * @param separator  区切り。画面ごとに違うので、呼ぶ側の文言から渡す
 */
export function axisValuesLabel(axisValues: readonly { name: string; value: string }[], separator: string): string {
  return axisValues.map((axis) => translate('common.axisValue', axis)).join(separator)
}
