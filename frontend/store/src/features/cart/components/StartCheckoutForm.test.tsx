import { describe, expect, test } from 'vitest'
import { StartCheckoutForm } from '@/features/cart/components/StartCheckoutForm'
import { renderStatic } from '@/test/render'

// 注文手続きに進む操作はサーバーアクションを通るので、サーバーアクションのテストと E2E で確認する（テストの書き方 第 1 節）

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
})
