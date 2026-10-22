import { describe, expect, test } from 'vitest'
import { CartItemControls } from '@/features/cart/components/CartItemControls'
import { quoteItem } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

// 数量の変更と削除はサーバーアクションを通るので、サーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）

describe('カートの品目の操作', () => {
  test('AC-78 今の数量を選んだ数量の選択と、小計と、削除を出す', () => {
    const html = renderMarkup(<CartItemControls item={quoteItem({ quantity: 3, subtotal: 3300 })} />)

    expect(html).toMatch(/<option[^>]* value="3" selected="">3<\/option>/)
    expect(html).toContain('3,300 円')
    expect(html).not.toContain('<s>')
    expect(html).toContain('>削除</button>')
  })

  test('AC-80 購入できない品目の小計は、合計に含めないので取り消しの要素（s）で出す', () => {
    const html = renderMarkup(
      <CartItemControls
        item={quoteItem({ purchasable: false, subtotal: 1100, unpurchasableReason: 'INSUFFICIENT_STOCK' })}
      />,
    )

    expect(html).toContain('<s>1,100 円</s>')
  })
})
