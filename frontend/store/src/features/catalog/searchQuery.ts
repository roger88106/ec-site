import type { Route } from 'next'
import { firstParam, pageParam, type SearchParams } from '@/lib/searchParams'

/** 検索のキーワードとページ。URL のクエリに持つ */
type SearchQuery = { q: string; page: number }

/**
 * URL のクエリから検索の条件を読む。
 *
 * 前後の空白は除き、形の合わないページは 1 ページ目にする。
 *
 * @param params 画面のクエリ
 * @returns 検索のキーワードとページ
 */
export function parseSearchQuery(params: SearchParams): SearchQuery {
  return { q: (firstParam(params.q) ?? '').trim(), page: pageParam(params.page) }
}

/**
 * 検索結果の URL を作る。
 *
 * 1 ページ目はページを書かない。
 *
 * @param query 検索のキーワードとページ
 * @returns 検索結果の URL
 */
export function searchHref(query: SearchQuery): Route {
  const params = new URLSearchParams({ q: query.q })
  if (query.page > 1) {
    params.set('page', String(query.page))
  }
  return `/search?${params}`
}
