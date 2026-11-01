import type { Route } from 'next'
import { productSortValues } from '@/api/generated/store'
import type { ProductSort } from '@/api/types'
import { translate } from '@/i18n/translate'
import { firstParam, pageParam, type SearchParams } from '@/lib/searchParams'

/** 商品一覧の絞り込み・並び順・ページ。URL のクエリに持つ（店舗画面の構成の「画面の URL」） */
export type ListQuery = {
  /** カテゴリ（子孫を含む）。null は絞り込まない */
  categoryId: string | null
  /** 税込価格の下限（円、この値を含む）。null は下限なし */
  minPrice: number | null
  /** 税込価格の上限（円、この値を含む）。null は上限なし */
  maxPrice: number | null
  sort: ProductSort
  /** ページ番号。1 始まり */
  page: number
}

/** 並び順の表示名 */
export function sortLabel(sort: ProductSort): string {
  return translate(`catalog.sort.${sort}`)
}

/** URL のクエリから読む。形の合わない値は指定なしとして扱う */
export function parseListQuery(params: SearchParams): ListQuery {
  const sort = firstParam(params.sort)
  return {
    categoryId: firstParam(params.categoryId) || null,
    minPrice: toPrice(firstParam(params.minPrice)),
    maxPrice: toPrice(firstParam(params.maxPrice)),
    sort: productSortValues.includes(sort as ProductSort) ? (sort as ProductSort) : 'newest',
    page: pageParam(params.page),
  }
}

/** 一覧の URL。既定の値（新着順、1 ページ目）と指定なしの条件は書かない */
export function listHref(query: ListQuery): Route {
  const params = new URLSearchParams()
  if (query.categoryId) params.set('categoryId', query.categoryId)
  if (query.minPrice !== null) params.set('minPrice', String(query.minPrice))
  if (query.maxPrice !== null) params.set('maxPrice', String(query.maxPrice))
  if (query.sort !== 'newest') params.set('sort', query.sort)
  if (query.page > 1) params.set('page', String(query.page))
  const search = params.toString()
  return search ? `/products?${search}` : '/products'
}

/** カテゴリだけで絞り込んだ一覧の URL */
export function categoryHref(categoryId: string): Route {
  return listHref({ categoryId, minPrice: null, maxPrice: null, sort: 'newest', page: 1 })
}

/** 0 以上の整数だけを価格として扱う */
function toPrice(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) {
    return null
  }
  return Number(value)
}
