import { LOCALE } from '@ec-site/shared/lib/locale'
import { translate } from '@/i18n/translate'

/** 日本時間の UTC からのずれ（日本に夏時間はない） */
const JST_OFFSET_MS = 9 * 60 * 60 * 1000

/** 金額の書式（3 桁ごとのカンマ） */
const yen = new Intl.NumberFormat(LOCALE)

/**
 * 価格の表示。税込価格を主に、税抜価格を括弧で併記する。<br />
 * 例：「1,980 円（税抜 1,800 円）」。有効な税率がなく税込価格がなければ、税抜価格だけを出す。
 */
export function formatPrice(priceInclTax: number | null | undefined, priceExclTax: number): string {
  if (priceInclTax == null) {
    return translate('common.priceExclTaxOnly', { exclTax: yen.format(priceExclTax) })
  }
  return translate('common.priceWithExclTax', { inclTax: yen.format(priceInclTax), exclTax: yen.format(priceExclTax) })
}

/**
 * 日時の入力欄（datetime-local）の値を、API に送る日時にする。<br />
 * 入力は日本時間とみなし、+09:00 のオフセット付きにする。空の入力は空白（null）。
 */
export function fromDateTimeInput(value: string): string | null {
  if (!value) {
    return null
  }
  return `${value}:00+09:00`
}

/** API の日時を、日時の入力欄（datetime-local）の日本時間の値「yyyy-MM-ddTHH:mm」にする */
export function toDateTimeInput(iso: string | null | undefined): string {
  if (!iso) {
    return ''
  }
  const parts = jstParts(iso)
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

/** 日本時間での年月日・時分（ゼロ埋め）。ブラウザのタイムゾーンに依存しないよう、UTC にずれを足して求める */
function jstParts(iso: string) {
  const jst = new Date(new Date(iso).getTime() + JST_OFFSET_MS)
  /** 2 桁にゼロ埋めする */
  const pad = (value: number) => String(value).padStart(2, '0')
  return {
    year: String(jst.getUTCFullYear()),
    month: pad(jst.getUTCMonth() + 1),
    day: pad(jst.getUTCDate()),
    hour: pad(jst.getUTCHours()),
    minute: pad(jst.getUTCMinutes()),
  }
}
