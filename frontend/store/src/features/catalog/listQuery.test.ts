import { describe, expect, test } from 'vitest'
import { categoryHref, listHref, parseListQuery } from '@/features/catalog/listQuery'

describe('商品一覧の URL のクエリ', () => {
  test('AC-52、53、54、57 絞り込み・並び順・ページを読む', () => {
    expect(
      parseListQuery({ categoryId: 'c1', minPrice: '1000', maxPrice: '5000', sort: 'price_asc', page: '2' }),
    ).toEqual({ categoryId: 'c1', minPrice: 1000, maxPrice: 5000, sort: 'price_asc', page: 2 })
  })

  test('AC-54 並び順の既定は新着順', () => {
    expect(parseListQuery({}).sort).toBe('newest')
    expect(parseListQuery({ sort: 'unknown' }).sort).toBe('newest')
  })

  test('形の合わない価格とページは、指定なしとして扱う', () => {
    const query = parseListQuery({ minPrice: '-1', maxPrice: 'abc', page: '0' })
    expect(query.minPrice).toBeNull()
    expect(query.maxPrice).toBeNull()
    expect(query.page).toBe(1)
  })

  test('空の入力欄（フォームの GET で空文字が来る）は、指定なしとして扱う', () => {
    expect(parseListQuery({ categoryId: '', minPrice: '' })).toMatchObject({ categoryId: null, minPrice: null })
  })

  test('同じ名前が複数あれば、最初の値を使う', () => {
    expect(parseListQuery({ page: ['3', '4'] }).page).toBe(3)
  })

  test('一覧の URL には、既定の値と指定なしの条件を書かない', () => {
    expect(listHref({ categoryId: null, minPrice: null, maxPrice: null, sort: 'newest', page: 1 })).toBe('/products')
    expect(listHref({ categoryId: 'c1', minPrice: 0, maxPrice: null, sort: 'price_desc', page: 2 })).toBe(
      '/products?categoryId=c1&minPrice=0&sort=price_desc&page=2',
    )
  })

  test('カテゴリだけで絞り込む一覧の URL は、ほかの条件を書かない', () => {
    expect(categoryHref('c1')).toBe('/products?categoryId=c1')
  })
})
