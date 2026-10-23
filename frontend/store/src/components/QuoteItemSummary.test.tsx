import { describe, expect, test } from 'vitest'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { quoteItem } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

describe('カート・注文手続きの品目', () => {
  test('AC-77 商品名、軸の値、単価（税込と税抜）を出し、商品の詳細へのリンクにする', () => {
    const html = renderMarkup(
      <QuoteItemSummary
        item={quoteItem({
          axisValues: [
            { name: '色', value: '赤' },
            { name: 'サイズ', value: 'M' },
          ],
        })}
      />,
    )

    expect(html).toContain('T シャツ')
    expect(html).toContain('色：赤　サイズ：M')
    expect(html).toContain('1,100 円')
    expect(html).toContain('税抜 1,000 円')
    expect(html).toContain('href="/products/00000000-0000-0000-0000-000000000101"')
    expect(html).not.toContain('購入できません')
  })

  test('AC-80 購入できない品目は「購入できません」と理由を出す', () => {
    const html = renderMarkup(
      <QuoteItemSummary item={quoteItem({ purchasable: false, unpurchasableReason: 'INSUFFICIENT_STOCK' })} />,
    )

    expect(html).toContain('購入できません')
    expect(html).toContain('在庫が足りません。数量を減らしてください')
  })
})
