import { describe, expect, test } from 'vitest'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { quoteItem } from '@/test/fixtures'
import { renderStatic } from '@/test/render'

describe('カート・注文手続きの品目', () => {
  test('AC-77 商品名、軸の値、単価（税込と税抜）を出し、商品の詳細へのリンクにする', () => {
    const view = renderStatic(
      <QuoteItemSummary
        item={quoteItem({
          axisValues: [
            { name: '色', value: '赤' },
            { name: 'サイズ', value: 'M' },
          ],
        })}
      />,
    )

    expect(view.getByRole('link', { name: 'T シャツ' })).toHaveAttribute(
      'href',
      '/products/00000000-0000-0000-0000-000000000101',
    )
    expect(view.getByText('色：赤 サイズ：M')).toBeInTheDocument()
    expect(view.getByText('1,100 円')).toBeInTheDocument()
    expect(view.getByText('（税抜 1,000 円）')).toBeInTheDocument()
    expect(view.queryByText('購入できません')).toBeNull()
  })

  test('AC-80 購入できない品目は「購入できません」と理由を出す', () => {
    const view = renderStatic(
      <QuoteItemSummary item={quoteItem({ purchasable: false, unpurchasableReason: 'INSUFFICIENT_STOCK' })} />,
    )

    expect(view.getByText('購入できません')).toBeInTheDocument()
    expect(view.getByText('在庫が足りません。数量を減らしてください')).toBeInTheDocument()
  })
})
