import { describe, expect, test } from 'vitest'
import { orderListHref, parseOrderListPage } from '@/features/order/orderListQuery'

describe('注文の一覧のページ', () => {
  test('AC-129 クエリの page から、一覧のページの番号を読む', () => {
    expect(parseOrderListPage({ page: '3' })).toBe(3)
    expect(parseOrderListPage({ page: ['2', '5'] })).toBe(2)
  })

  test('AC-129 page がない・形の違う値・1 より小さい値なら、1 ページ目にする', () => {
    expect(parseOrderListPage({})).toBe(1)
    expect(parseOrderListPage({ page: 'abc' })).toBe(1)
    expect(parseOrderListPage({ page: '0' })).toBe(1)
    expect(parseOrderListPage({ page: '1.5' })).toBe(1)
  })

  test('AC-129 ページの URL は、1 ページ目ならクエリを付けず、2 ページ目以降なら page を付ける', () => {
    expect(orderListHref(1)).toBe('/orders')
    expect(orderListHref(2)).toBe('/orders?page=2')
  })
})
