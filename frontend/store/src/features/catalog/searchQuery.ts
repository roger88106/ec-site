import type { Route } from 'next'
import { firstParam, pageParam, type SearchParams } from '@/lib/searchParams'

/** 検索のキーワードとページ。URL のクエリに持つ */
type SearchQuery = { q: string; page: number }

/** URL のクエリから読む。前後の空白は除き、形の合わないページは 1 ページ目にする */
export function parseSearchQuery(params: SearchParams): SearchQuery {
  return { q: (firstParam(params.q) ?? '').trim(), page: pageParam(params.page) }
}

/** 検索結果の URL。1 ページ目はページを書かない */
export function searchHref(query: SearchQuery): Route {
  const params = new URLSearchParams({ q: query.q })
  if (query.page > 1) params.set('page', String(query.page))
  return `/search?${params}`
}
