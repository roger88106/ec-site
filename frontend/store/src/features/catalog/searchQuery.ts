import type { SearchParams } from '@/features/catalog/listQuery'

/** 検索のキーワードとページ（AC-58、61）。URL のクエリに持つ */
export type SearchQuery = { q: string; page: number }

/** URL のクエリから読む。前後の空白は除き、形の合わないページは 1 ページ目にする */
export function parseSearchQuery(params: SearchParams): SearchQuery {
  const q = Array.isArray(params.q) ? params.q[0] : params.q
  const page = Number(Array.isArray(params.page) ? params.page[0] : params.page)
  return { q: (q ?? '').trim(), page: Number.isInteger(page) && page >= 1 ? page : 1 }
}

/** 検索結果の URL。1 ページ目はページを書かない */
export function searchHref(query: SearchQuery): string {
  const params = new URLSearchParams({ q: query.q })
  if (query.page > 1) params.set('page', String(query.page))
  return `/search?${params}`
}
