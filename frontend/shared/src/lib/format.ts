import { LOCALE, TIME_ZONE } from './locale'
import { translate } from './translate'

/**
 * 既定の数の書式（3 桁ごとのカンマ）
 *
 * 金額の整形も使う。
 */
const number = new Intl.NumberFormat(LOCALE)

/** 数を、単位を付けずに 3 桁ごとに区切る（例：「1,234」）。 */
export function formatNumber(value: number): string {
  return number.format(value)
}

/**
 * 日時の書式（日本時間の年・月・日・時・分をゼロ埋めで出す。例：「2026/10/10 14:05」）
 *
 * 管理画面の日時の入力欄も使う。
 */
export const japanDateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** 金額に「円」を付ける（例：「1,000 円」）。 */
export function formatYen(amount: number): string {
  return translate('common.yen', { amount: formatNumber(amount) }) // 金額に「円」を付ける
}

/**
 * 価格を、税込価格を主に、税抜価格を括弧で併記して表示する。
 *
 * 例：「1,980 円（税抜 1,800 円）」。有効な税率がなく税込価格がなければ、税抜価格だけを出す。
 *
 * @param price 税込価格（なければ null）と税抜価格
 * @returns 表示する価格
 */
export function formatPrice(price: { inclTax?: number | null; exclTax: number }): string {
  if (price.inclTax == null) {
    return translate('common.priceExclTaxOnly', { exclTax: formatNumber(price.exclTax) }) // 税抜の金額と、税込を計算できない案内
  }
  return translate('common.priceWithExclTax', {
    inclTax: formatNumber(price.inclTax),
    exclTax: formatNumber(price.exclTax),
  }) // 税込と税抜の金額
}

/**
 * 日時を、日本時間の「yyyy/MM/dd HH:mm」で表示する。
 *
 * 空白（null）は空文字にする。
 *
 * @param iso 日時（ISO 8601）
 * @returns 表示する日時
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  return japanDateTimeFormat.format(new Date(iso))
}

/** 1 つの税率の消費税額を表示する（例：「10% 対象 11,000 円（内消費税 1,000 円）」）。 */
export function taxLabel(tax: { ratePercent: number; taxableAmount: number; taxAmount: number }): string {
  return translate('common.taxAmount', {
    ratePercent: tax.ratePercent,
    taxableAmount: formatYen(tax.taxableAmount),
    taxAmount: formatYen(tax.taxAmount),
  }) // 税率、課税対象額、消費税額
}

/** 郵便番号にハイフンを入れる（API はハイフンなしの 7 桁を返す。例：「100-0001」）。 */
export function formatPostalCode(postalCode: string): string {
  return `${postalCode.slice(0, 3)}-${postalCode.slice(3)}`
}

/**
 * 軸の名前と値を並べる（例：「色：黒　サイズ：M」）。
 *
 * @param axisValues 軸の名前と値
 * @param separator  区切り（画面ごとの文言）
 * @returns 並べた表示
 */
export function axisValuesLabel(axisValues: readonly { name: string; value: string }[], separator: string): string {
  return axisValues.map((axis) => translate('common.axisValue', axis)).join(separator) // 軸の名前：値
}
