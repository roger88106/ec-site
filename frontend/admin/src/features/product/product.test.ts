import { describe, expect, test } from 'vitest'
import { EMPTY_PRODUCT_FIELDS, isProductFieldsFilled, toProductBody } from '@/features/product/product'

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
