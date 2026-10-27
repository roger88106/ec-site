// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { changeCartItemQuantity, removeCartItem } from '@/actions/cart'
import { QuoteItemSummary } from '@/components/QuoteItemSummary'
import { CartItemControls } from '@/features/cart/components/CartItemControls'
import { quoteItem } from '@/test/fixtures'
import { renderInBrowser, renderStatic } from '@/test/render'

// 数量の変更と削除はサーバーアクションを通るので、結果の表示だけを、差し替えた結果で確認する。動作はサーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）
vi.mock('@/actions/cart', () => ({
  changeCartItemQuantity: vi.fn(async () => ({ ok: true })),
  removeCartItem: vi.fn(async () => ({ ok: true })),
}))

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

  test('AC-193-1 削除の失敗の文は、送る前からある状態の知らせ（status）の領域に入る。領域は 1 つだけ', async () => {
    vi.mocked(removeCartItem).mockResolvedValueOnce({
      ok: false,
      message: '在庫が足りません。',
      errorCode: 'OUT_OF_STOCK',
    })
    const { user } = renderInBrowser(<CartItemControls item={quoteItem({ quantity: 3, subtotal: 3300 })} />)

    // 送る前も、領域は 1 つあり、文はない
    expect(screen.getAllByRole('status')).toHaveLength(1)
    const statusRegion = screen.getByRole('status')
    expect(statusRegion).toBeEmptyDOMElement()

    await user.click(screen.getByRole('button', { name: '削除' }))

    // 失敗の後は、同じ領域に文が入る
    expect(await screen.findByText('在庫が足りません。')).toBeInTheDocument()
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(statusRegion)
    expect(statusRegion).toHaveTextContent('在庫が足りません。')
  })

  test('AC-193-1 数量の変更の失敗の文は、送る前からある状態の知らせ（status）の領域に入る。領域は 1 つだけ', async () => {
    vi.mocked(changeCartItemQuantity).mockResolvedValueOnce({
      ok: false,
      message: '在庫が足りません。',
      errorCode: 'OUT_OF_STOCK',
    })
    const { user } = renderInBrowser(<CartItemControls item={quoteItem({ quantity: 3, subtotal: 3300 })} />)

    // 送る前も、領域は 1 つあり、文はない
    expect(screen.getAllByRole('status')).toHaveLength(1)
    const statusRegion = screen.getByRole('status')
    expect(statusRegion).toBeEmptyDOMElement()

    await user.selectOptions(screen.getByRole('combobox', { name: '数量' }), '2')

    // 失敗の後は、同じ領域に文が入る
    expect(await screen.findByText('在庫が足りません。')).toBeInTheDocument()
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(statusRegion)
    expect(statusRegion).toHaveTextContent('在庫が足りません。')
  })
})
