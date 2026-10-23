import type { Route } from 'next'
import { pageParam, type SearchParams } from '@/lib/searchParams'

/**
 * 注文の一覧（/orders）のクエリの page を読む。
 *
 * @param params 画面のクエリ
 * @returns ページ（1 から）。ない・形の違う値なら 1
 */
export function parseOrderListPage(params: SearchParams): number {
  return pageParam(params.page)
}

/**
 * 注文の一覧のページの URL。
 *
 * @param page ページ（1 から）
 */
export function orderListHref(page: number): Route {
  const params = new URLSearchParams()
  if (page > 1) params.set('page', String(page))
  const search = params.toString()
  return search ? `/orders?${search}` : '/orders'
}
