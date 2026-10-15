import { generatePath } from 'react-router'

/**
 * 画面の URL の形（管理画面の構成 第 3 節）。ルーターの定義はこれを使う。<br />
 * 画面のコードは URL の文字列を直接書かず、{@link paths} で作る。
 */
export const routePatterns = {
  top: '/',
  products: '/products',
  productNew: '/products/new',
  product: '/products/:productId',
  stock: '/products/:productId/variants/:variantId/stock',
  categories: '/categories',
  taxCategories: '/tax-categories',
  taxCategory: '/tax-categories/:taxCategoryId',
  orders: '/orders',
  order: '/orders/:orderId',
  salesTrend: '/reports/sales-trend',
  variantSales: '/reports/variant-sales',
  customerBreakdown: '/reports/customer-breakdown',
} as const

/**
 * 画面の URL を作る。ID などの値は、URL の形のパラメーターの名前と型で確かめる（generatePath）。<br />
 * 一覧の絞り込みのクエリは、各機能の *Filter.ts が作る。
 */
export const paths = {
  /** 最初の画面（商品の一覧に移る） */
  top: () => routePatterns.top,
  /** 商品の一覧 */
  products: () => routePatterns.products,
  /** 商品の登録 */
  productNew: () => routePatterns.productNew,
  /** 商品の編集 */
  product: (productId: string) => generatePath(routePatterns.product, { productId }),
  /** バリアントの在庫 */
  stock: (productId: string, variantId: string) => generatePath(routePatterns.stock, { productId, variantId }),
  /** カテゴリ */
  categories: () => routePatterns.categories,
  /** 税区分の一覧 */
  taxCategories: () => routePatterns.taxCategories,
  /** 税区分の税率 */
  taxCategory: (taxCategoryId: string) => generatePath(routePatterns.taxCategory, { taxCategoryId }),
  /** 注文の一覧 */
  orders: () => routePatterns.orders,
  /** 注文の詳細 */
  order: (orderId: string) => generatePath(routePatterns.order, { orderId }),
  /** 売上の推移（条件のクエリは reportCondition.ts が作る） */
  salesTrend: () => routePatterns.salesTrend,
  /** バリエーションの売れ行き */
  variantSales: () => routePatterns.variantSales,
  /** 地域・年齢層・性別 */
  customerBreakdown: () => routePatterns.customerBreakdown,
}
