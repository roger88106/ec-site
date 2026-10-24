import { describe, expect, test } from 'vitest'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { CartItemControls } from '@/features/cart/components/CartItemControls'
import { quoteItem } from '@/test/fixtures'
import { renderStatic } from '@/test/render'

// 数量の変更と削除はサーバーアクションを通るので、サーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）

describe('カートの品目の操作', () => {
  test('AC-78 今の数量を選んだ数量の選択と、小計と、削除を出す', () => {
    const view = renderStatic(<CartItemControls item={quoteItem({ quantity: 3, subtotal: 3300 })} />)

    expect(view.getByRole('combobox', { name: '数量' })).toHaveValue('3')
    expect(view.getByText('3,300 円')).toBeInTheDocument()
    expect(view.getByRole('button', { name: '削除' })).toBeInTheDocument()
  })

  // 画面では、品目の概要（理由を出す）の中に操作を置く（app/cart/page.tsx）ので、同じ組み合わせで確認する
  test('AC-80 購入できない品目は、購入できる品目と違い、「購入できません」と理由を示しながら小計の金額を出す', () => {
    const purchasable = renderStatic(
      <QuoteItemSummary item={quoteItem({ quantity: 2, subtotal: 2200 })}>
        <CartItemControls item={quoteItem({ quantity: 2, subtotal: 2200 })} />
      </QuoteItemSummary>,
    )

    expect(purchasable.getByText('2,200 円')).toBeInTheDocument()
    expect(purchasable.queryByText('購入できません')).not.toBeInTheDocument()
    expect(purchasable.queryByText('在庫が足りません。数量を減らしてください')).not.toBeInTheDocument()

    const unpurchasable = renderStatic(
      <QuoteItemSummary
        item={quoteItem({
          quantity: 2,
          subtotal: 2200,
          purchasable: false,
          unpurchasableReason: 'INSUFFICIENT_STOCK',
        })}
      >
        <CartItemControls
          item={quoteItem({
            quantity: 2,
            subtotal: 2200,
            purchasable: false,
            unpurchasableReason: 'INSUFFICIENT_STOCK',
          })}
        />
      </QuoteItemSummary>,
    )

    expect(unpurchasable.getByText('購入できません')).toBeInTheDocument()
    expect(unpurchasable.getByText('在庫が足りません。数量を減らしてください')).toBeInTheDocument()
    // 合計に含めない小計も金額は残す（取り消し線は見た目なので目視で確認する。テストの書き方 第 1 節）
    expect(unpurchasable.getByText('2,200 円')).toBeInTheDocument()
  })
})
