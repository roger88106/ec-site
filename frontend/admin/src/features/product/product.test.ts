import { describe, expect, test } from 'vitest'
import { EMPTY_PRODUCT_FIELDS, isNeverShown, isProductFieldsFilled, toProductBody } from '@/features/product/product'

/** テストの現在時刻 */
const NOW = Date.parse('2026-10-08T12:00:00+09:00')

describe('C 側に表示されたことがあるか', () => {
  test('AC-33 初回表示日時が空白なら、表示されていない', () => {
    expect(isNeverShown({ firstShownAt: null }, NOW)).toBe(true)
  })

  test('AC-33 初回表示日時が未来（公開の予約だけ）なら、表示されていない', () => {
    expect(isNeverShown({ firstShownAt: '2026-10-09T00:00:00+09:00' }, NOW)).toBe(true)
  })

  test('AC-33 初回表示日時が現在以前なら、表示されたことがある', () => {
    expect(isNeverShown({ firstShownAt: '2026-10-08T12:00:00+09:00' }, NOW)).toBe(false)
  })
})

describe('商品の基本情報の入力', () => {
  test('税抜価格を数値にして送る', () => {
    const body = toProductBody({ ...EMPTY_PRODUCT_FIELDS, name: 'T シャツ', taxCategoryId: 't1', priceExclTax: '1800' })
    expect(body).toEqual({
      name: 'T シャツ',
      description: '',
      taxCategoryId: 't1',
      priceExclTax: 1800,
      categoryIds: [],
    })
  })

  test('商品名・税区分・税抜価格がそろったら送れる', () => {
    expect(isProductFieldsFilled(EMPTY_PRODUCT_FIELDS)).toBe(false)
    expect(isProductFieldsFilled({ ...EMPTY_PRODUCT_FIELDS, name: 'a', taxCategoryId: 't1', priceExclTax: '1' })).toBe(
      true,
    )
  })
})
