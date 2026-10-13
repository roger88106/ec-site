import type { Route } from 'next'

/**
 * 注文の一覧（/orders）のクエリの page を読む（AC-129）。
 *
 * @param params 画面のクエリ
 * @returns ページ（1 から）。ない・形の違う値なら 1
 */
export function parseOrderListPage(params: Record<string, string | string[] | undefined>): number {
  const page = Number(Array.isArray(params.page) ? params.page[0] : params.page)
  return Number.isInteger(page) && page >= 1 ? page : 1
}

/**
 * 注文の一覧のページの URL。
 *
 * @param page ページ（1 から）
 */
export function orderListHref(page: number): Route {
  return page > 1 ? `/orders?page=${page}` : '/orders'
}
