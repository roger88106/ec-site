import type { Cart, QuoteItem } from '@/api/types'

/**
 * サーバーの処理のテストで API の応答に使うデータ（店舗画面の構成 第 7 節）。<br />
 * どれも、テストに関係のない項目にありふれた値を入れたもの。テストで確かめる項目は overrides で与える。
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
