import { describe, expect, test } from 'vitest'
import { EMPTY_FILTER, parseFilter, toSearchParams } from '@/features/order/orderFilter'

describe('注文一覧の絞り込みと URL', () => {
  test('AC-98 絞り込みとページを URL のクエリにし、読み戻せる', () => {
    const filter = {
      orderNumber: '20261010',
      status: 'CANCELLATION_REQUESTED' as const,
      orderedFrom: '2026-10-01',
      orderedTo: '2026-10-10',
      page: 3,
    }
    const params = toSearchParams(filter)
    expect(params.toString()).toBe(
      'orderNumber=20261010&status=CANCELLATION_REQUESTED&orderedFrom=2026-10-01&orderedTo=2026-10-10&page=3',
    )
    expect(parseFilter(params)).toEqual(filter)
  })

  test('空の条件と 1 ページ目は URL に書かない', () => {
    expect(toSearchParams(EMPTY_FILTER).toString()).toBe('')
  })

  test('不正な状態・日付・ページは無視する', () => {
    expect(parseFilter(new URLSearchParams('status=UNKNOWN&orderedFrom=2026/10/01&orderedTo=abc&page=0'))).toEqual(
      EMPTY_FILTER,
    )
  })
})
