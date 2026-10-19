import { LOCALE } from '@ec-site/shared/lib/locale'
import type { Price } from '@/api/types'

/** 金額の書式（3 桁ごとのカンマ） */
const yen = new Intl.NumberFormat(LOCALE)

/**
 * 税込・税抜の金額を 3 桁区切りの文字にする（部品で大きさを変えて出すため）。<br />
 * 価格を 1 つの文で出すときは、共有パッケージの formatPrice を使う。
 */
export function yenParts(price: Price): { inclTax: string; exclTax: string } {
  return { inclTax: yen.format(price.inclTax), exclTax: yen.format(price.exclTax) }
}
