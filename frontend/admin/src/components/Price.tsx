import { formatPrice } from '@/lib/format'

/** 価格。例：「1,980 円（税抜 1,800 円）」 */
export function Price({ inclTax, exclTax }: { inclTax: number | null | undefined; exclTax: number }) {
  return <span className="tabular-nums">{formatPrice(inclTax, exclTax)}</span>
}
