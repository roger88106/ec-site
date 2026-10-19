import type {
  OrderActions,
  OrderDetail,
  OrderSummary,
  ProductActions,
  ProductDetail,
  TaxCategory,
  Variant,
} from '@/api/types'

/**
 * 結合テストで API の応答に使うデータ（管理画面の構成 第 9 節）。<br />
 * どれも、テストに関係のない項目にありふれた値を入れたもの。テストで確かめる項目は overrides で与える。
 */

/** テストのデータの税区分の ID */
export const TAX_CATEGORY_ID = '00000000-0000-0000-0000-000000000001'
/** テストのデータの商品の ID */
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
    images: [],
    variants: [],
    actions: productActions(),
    version: 0,
    createdAt: '2026-10-01T00:00:00+09:00',
    updatedAt: '2026-10-01T00:00:00+09:00',
    ...overrides,
  }
}

/** 非公開で、C 側に表示されたことのない商品にできる操作（API が返す旗） */
export function productActions(overrides: Partial<ProductActions> = {}): ProductActions {
  return {
    publish: true,
    unpublish: false,
    discontinue: true,
    delete: true,
    deleteVariants: true,
    addVariant: true,
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

/** テストのデータの注文の ID */
export const ORDER_ID = '00000000-0000-0000-0000-000000000501'

/** 「受付」の注文にできる操作（API が返す旗） */
export function orderActions(overrides: Partial<OrderActions> = {}): OrderActions {
  return { approveCancellation: false, rejectCancellation: false, cancel: true, ship: false, ...overrides }
}

/** 一覧の「受付」の注文 1 件 */
export function orderSummary(overrides: Partial<OrderSummary> = {}): OrderSummary {
  return {
    id: ORDER_ID,
    orderNumber: '20261010-123456',
    orderedAt: '2026-10-10T01:00:00Z',
    customerName: '山田 太郎',
    total: 4300,
    status: 'ACCEPTED',
    authorizationExpiring: false,
    ...overrides,
  }
}

/** 10% の商品 1 品目（税込 1,100 円 × 3）と送料の、「受付」の注文。申請・キャンセルはない */
export function orderDetail(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: ORDER_ID,
    orderNumber: '20261010-123456',
    orderedAt: '2026-10-10T01:00:00Z',
    status: 'ACCEPTED',
    lines: [
      {
        variantId: '00000000-0000-0000-0000-000000000201',
        productCode: 'ABC-001',
        variantCode: 'ABC-001-RED',
        productName: 'T シャツ',
        axisValues: [{ name: '色', value: '赤' }],
        unitPriceExclTax: 1000,
        unitPriceInclTax: 1100,
        ratePercent: 10,
        quantity: 3,
        subtotal: 3300,
      },
    ],
    amounts: {
      itemsTotal: 3300,
      shippingFee: 1000,
      total: 4300,
      taxes: [{ ratePercent: 10, taxableAmount: 4300, taxAmount: 390 }],
    },
    email: 'taro@example.com',
    actions: orderActions(),
    shippingAddress: {
      name: '山田 太郎',
      postalCode: '1000001',
      prefectureCode: '13',
      prefectureName: '東京都',
      municipalityCode: '13101',
      municipalityName: '千代田区',
      address: '千代田 1-1',
      phone: '0312345678',
    },
    ...overrides,
  }
}
