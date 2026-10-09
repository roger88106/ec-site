import type { ProductDetail, TaxCategory, Variant } from '@/api/types'

/**
 * 結合テストで API の応答に使うデータ（管理画面の構成 第 10 節）。<br />
 * どれも、テストに関係のない項目にありふれた値を入れたもの。テストで確かめる項目は overrides で与える。
 */

export const TAX_CATEGORY_ID = '00000000-0000-0000-0000-000000000001'
export const PRODUCT_ID = '00000000-0000-0000-0000-000000000101'

/** 「標準」に 10%（開始・終了とも空白）だけがある税区分 */
export function taxCategory(overrides: Partial<TaxCategory> = {}): TaxCategory {
  return {
    id: TAX_CATEGORY_ID,
    name: '標準',
    currentRatePercent: 10,
    rates: [{ id: '00000000-0000-0000-0000-000000000011', ratePercent: 10, effectiveFrom: null, effectiveTo: null }],
    version: 0,
    ...overrides,
  }
}

/** 色の軸を持ち、バリアントのない、非公開の商品（C 側に表示されたことはない） */
export function productDetail(overrides: Partial<ProductDetail> = {}): ProductDetail {
  return {
    id: PRODUCT_ID,
    productCode: 'ABC-001',
    name: 'T シャツ',
    description: '',
    taxCategoryId: TAX_CATEGORY_ID,
    priceExclTax: 1000,
    priceInclTax: 1100,
    categoryIds: [],
    axes: ['色'],
    status: 'UNPUBLISHED',
    firstShownAt: null,
    publishStart: null,
    publishEnd: null,
    variants: [],
    version: 0,
    createdAt: '2026-10-01T00:00:00+09:00',
    updatedAt: '2026-10-01T00:00:00+09:00',
    ...overrides,
  }
}

/** 販売中のバリアント */
export function variant(overrides: Partial<Variant> = {}): Variant {
  return {
    id: '00000000-0000-0000-0000-000000000201',
    variantCode: 'ABC-001-RED',
    axisValues: ['赤'],
    salesSuspended: false,
    ...overrides,
  }
}
