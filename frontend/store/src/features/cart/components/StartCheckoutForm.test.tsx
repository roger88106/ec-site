import { describe, expect, test } from 'vitest'
import { StartCheckoutForm } from '@/features/cart/components/StartCheckoutForm'
import { renderMarkup } from '@/test/render'

// 注文手続きに進む操作はサーバーアクションを通るので、サーバーアクションのテストと E2E で確かめる（テストの書き方 第 1 節）

describe('注文手続きに進む', () => {
  test('購入できる品目だけのカートは、進める', () => {
    const html = renderMarkup(<StartCheckoutForm orderable />)

    expect(html).toContain('>注文手続きへ進む</button>')
    expect(html).not.toContain(' disabled=""')
    expect(html).not.toContain('購入できない商品をカートから削除すると')
  })

  test('AC-80 購入できない品目を含むカートは進めず、理由を出す', () => {
    const html = renderMarkup(<StartCheckoutForm orderable={false} />)

    expect(html).toMatch(/<button type="submit"[^>]* disabled=""[^>]*>注文手続きへ進む<\/button>/)
    expect(html).toContain('購入できない商品をカートから削除すると、注文手続きに進めます。')
  })
})
