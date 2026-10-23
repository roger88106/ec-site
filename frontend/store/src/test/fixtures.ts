import type { Cart, Order, QuoteItem, ShippingAddress, Variant, VariantAxis } from '@/api/types'

/**
 * サーバーの処理のテストで API の応答に使うデータ（店舗画面の構成 第 7 節）。<br />
 * どれも、テストに関係のない項目にありふれた値を入れたもの。テストで確認する項目は overrides で与える。
 */

/** Cookie のカートの ID */
export const COOKIE_CART_ID = '00000000-0000-0000-0000-0000000000c1'
/** 新しく作ったカートの ID */
export const NEW_CART_ID = '00000000-0000-0000-0000-0000000000c2'
/** 会員のカートの ID */
export const MEMBER_CART_ID = '00000000-0000-0000-0000-0000000000c3'
/** バリアントの ID */
export const VARIANT_ID = '00000000-0000-0000-0000-000000000201'
/** 注文手続きの ID */
export const CHECKOUT_ID = '00000000-0000-0000-0000-000000000401'
/** 注文の ID */
export const ORDER_ID = '00000000-0000-0000-0000-000000000501'

/** 10% の商品（税込 1,100 円）の品目 1 つ */
export function quoteItem(overrides: Partial<QuoteItem> = {}): QuoteItem {
  return {
    variantId: VARIANT_ID,
    productId: '00000000-0000-0000-0000-000000000101',
    productName: 'T シャツ',
    axisValues: [{ name: '色', value: '赤' }],
    imageUrl: null,
    unitPrice: { inclTax: 1100, exclTax: 1000 },
    quantity: 1,
    subtotal: 1100,
    purchasable: true,
    ...overrides,
  }
}

/** お届け先 */
export const SHIPPING_ADDRESS: ShippingAddress = {
  name: '山田 太郎',
  postalCode: '1000001',
  prefectureCode: '13',
  prefectureName: '東京都',
  municipalityCode: '13101',
  municipalityName: '千代田区',
  address: '千代田 1-1',
  phone: '0312345678',
}

/** 受付済みの注文（キャンセルを申請できる。商品の行はない） */
export function order(overrides: Partial<Order> = {}): Order {
  return {
    id: ORDER_ID,
    orderNumber: '20261010-482915',
    orderedAt: '2026-10-10T03:00:00Z',
    status: 'ACCEPTED',
    lines: [],
    amounts: { itemsTotal: 0, shippingFee: 0, total: 0, taxes: [] },
    email: 'member@example.com',
    shippingAddress: SHIPPING_ADDRESS,
    actions: { requestCancellation: true, pay: false },
    ...overrides,
  }
}

/** 色とサイズの軸 */
export const VARIANT_AXES: VariantAxis[] = [
  { name: '色', values: ['赤', '青'] },
  { name: 'サイズ', values: ['M', 'L'] },
]

/**
 * 色とサイズの値を持つバリアント。既定は在庫があり、販売停止でない。
 *
 * @param color     色の値
 * @param size      サイズの値
 * @param overrides 上書きする項目
 */
export function variant(color: string, size: string, overrides: Partial<Variant> = {}): Variant {
  return {
    id: `variant-${color}-${size}`,
    axisValues: [color, size],
    inStock: true,
    salesSuspended: false,
    imageUrl: null,
    ...overrides,
  }
}

/** 品目を持つカート */
export function cart(items: QuoteItem[], overrides: Partial<Cart> = {}): Cart {
  const itemsTotal = items.reduce((sum, item) => sum + item.subtotal, 0)
  return {
    id: COOKIE_CART_ID,
    items,
    amounts: { itemsTotal, shippingFee: 1000, total: itemsTotal + 1000, taxes: [] },
    orderable: true,
    ...overrides,
  }
}

/**
 * フォームの値を作る。
 *
 * @param fields 項目の名前 → 値
 */
export function form(fields: Record<string, string>): FormData {
  const data = new FormData()
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value)
  }
  return data
}
