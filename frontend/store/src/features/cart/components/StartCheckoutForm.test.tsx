// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { startCheckout } from '@/actions/cart'
import { StartCheckoutForm } from '@/features/cart/components/StartCheckoutForm'
import { renderInBrowser, renderStatic } from '@/test/render'

// 注文手続きに進む操作はサーバーアクションを通るので、結果の表示だけを、差し替えた結果で確認する。動作はサーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）
vi.mock('@/actions/cart', () => ({ startCheckout: vi.fn(async () => ({ ok: true })) }))

describe('注文手続きに進む', () => {
  test('購入できる品目だけのカートは、進める', () => {
    const view = renderStatic(<StartCheckoutForm orderable />)

    expect(view.getByRole('button', { name: '注文手続きへ進む' })).toBeEnabled()
    expect(view.queryByText('購入できない商品をカートから削除すると、注文手続きに進めます。')).not.toBeInTheDocument()
  })

  test('AC-80 購入できない品目を含むカートは進めず、理由を出す', () => {
    const view = renderStatic(<StartCheckoutForm orderable={false} />)

    expect(view.getByRole('button', { name: '注文手続きへ進む' })).toBeDisabled()
    expect(view.getByText('購入できない商品をカートから削除すると、注文手続きに進めます。')).toBeInTheDocument()
  })

  test('AC-193-1 失敗の文は、送る前からある状態の知らせ（status）の領域に入る', async () => {
    vi.mocked(startCheckout).mockResolvedValueOnce({
      ok: false,
      message: '在庫が足りません。',
      errorCode: 'OUT_OF_STOCK',
    })
    const { user } = renderInBrowser(<StartCheckoutForm orderable />)

    // 送る前も、領域は 1 つあり、文はない
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBeEmptyDOMElement()

    await user.click(screen.getByRole('button', { name: '注文手続きへ進む' }))

    // 失敗の後は、同じ領域に文が入る
    expect(await screen.findByText('在庫が足りません。')).toBeInTheDocument()
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent('在庫が足りません。')
  })
})
