import { describe, expect, test } from 'vitest'
import { EMPTY_FILTER, parseFilter, toSearchParams } from '@/features/product/productFilter'

describe('商品の一覧の絞り込みと URL', () => {
  test('AC-48 絞り込みとページを URL のクエリにし、読み戻せる', () => {
    const filter = { code: 'TEE', name: 'シャツ', status: 'PUBLISHED' as const, categoryId: 'c1', page: 2 }
    const params = toSearchParams(filter)
    expect(params.toString()).toBe('code=TEE&name=%E3%82%B7%E3%83%A3%E3%83%84&status=PUBLISHED&categoryId=c1&page=2')
    expect(parseFilter(params)).toEqual(filter)
  })

  test('空の条件と 1 ページ目は URL に書かない', () => {
    expect(toSearchParams(EMPTY_FILTER).toString()).toBe('')
  })

  test('不正な状態とページは無視する', () => {
    expect(parseFilter(new URLSearchParams('status=UNKNOWN&page=0'))).toEqual(EMPTY_FILTER)
    expect(parseFilter(new URLSearchParams('page=abc')).page).toBe(1)
  })
})
