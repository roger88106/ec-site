import { LOCALE } from '@ec-site/shared/lib/locale'
import type { Price } from '@/api/types'

/** 金額の書式（3 桁ごとのカンマ） */
const yen = new Intl.NumberFormat(LOCALE)

/** 価格の表示（BR-4）。税込価格を主に、税抜価格を括弧で併記する（例：「1,980 円（税抜 1,800 円）」） */
export function formatPrice(price: Price): string {
  return `${yen.format(price.inclTax)} 円（税抜 ${yen.format(price.exclTax)} 円）`
}

/** 税込・税抜の金額を 3 桁区切りの文字にする（部品で大きさを変えて出すため） */
export function yenParts(price: Price): { inclTax: string; exclTax: string } {
  return { inclTax: yen.format(price.inclTax), exclTax: yen.format(price.exclTax) }
}
