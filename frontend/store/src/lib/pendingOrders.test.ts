import { describe, expect, test } from 'vitest'
import { isLimitReached, pendingOrdersLimitHref } from '@/lib/pendingOrders'

describe('支払待ちの注文の確認の画面のクエリ', () => {
  test('AC-411-9 limit=reached なら、上限に達したことを示す', () => {
    expect(isLimitReached({ limit: 'reached' })).toBe(true)
  })

  test.each([{}, { limit: 'other' }, { limit: '' }, { limit: undefined }, { limit: ['reached', 'x'] }])(
    'AC-411-9 ない・他の値・複数の値なら、上限に達したことを示さない（%j）',
    (params) => {
      expect(isLimitReached(params)).toBe(false)
    },
  )

  test('AC-411-9 上限に達したことを示す URL は、読み取りで真になるクエリを付けた確認の画面である', () => {
    const [path, query] = pendingOrdersLimitHref().split('?')

    expect(path).toBe('/checkout/pending-orders')
    expect(isLimitReached(Object.fromEntries(new URLSearchParams(query)))).toBe(true)
  })
})
