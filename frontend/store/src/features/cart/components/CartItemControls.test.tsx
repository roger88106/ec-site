// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { CartItemControls } from '@/features/cart/components/CartItemControls'
import { quoteItem } from '@/test/fixtures'
import { renderInBrowser } from '@/test/render'

// 数量の変更と削除はサーバーアクションを通るので、サーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）

describe('カートの品目の操作', () => {
  test('AC-78 今の数量を選んだ数量の選択と、小計と、削除を出す', () => {
    renderInBrowser(<CartItemControls item={quoteItem({ quantity: 3, subtotal: 3300 })} />)

    expect((screen.getByLabelText('数量') as HTMLSelectElement).value).toBe('3')
    expect(screen.getByText('3,300 円').closest('s')).toBeNull()
    expect(screen.getByRole('button', { name: '削除' })).toBeDefined()
  })

  test('AC-80 購入できない品目の小計は、合計に含めないので取り消しの要素で出す', () => {
    renderInBrowser(
      <CartItemControls
        item={quoteItem({ purchasable: false, subtotal: 1100, unpurchasableReason: 'INSUFFICIENT_STOCK' })}
      />,
    )

    expect(screen.getByText('1,100 円').tagName).toBe('S')
  })
})
