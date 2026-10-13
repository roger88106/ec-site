import type { Route } from 'next'
import { productSortValues } from '@/api/generated/store'
import type { ProductSort } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 商品一覧の絞り込み・並び順・ページ。URL のクエリに持つ（店舗画面の構成 第 3 節） */
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

/** Next.js の searchParams（同じ名前が複数あれば配列） */
export type SearchParams = Record<string, string | string[] | undefined>

/** 並び順の表示名 */
export function sortLabel(sort: ProductSort): string {
  return translate(`catalog.sort.${sort}`)
}

/** URL のクエリから読む。形の合わない値は指定なしとして扱う */
export function parseListQuery(params: SearchParams): ListQuery {
  const sort = first(params.sort)
  return {
    categoryId: first(params.categoryId) || null,
    minPrice: toPrice(first(params.minPrice)),
    maxPrice: toPrice(first(params.maxPrice)),
    sort: productSortValues.includes(sort as ProductSort) ? (sort as ProductSort) : 'newest',
    page: toPage(first(params.page)),
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

/** 同じ名前のパラメータが複数あれば、最初のものを使う */
function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

/** 0 以上の整数だけを価格として扱う */
function toPrice(value: string | undefined): number | null {
  if (!value || !/^\d+$/.test(value)) {
    return null
  }
  return Number(value)
}

/** 1 以上の整数だけをページ番号として扱い、それ以外は 1 ページ目にする */
function toPage(value: string | undefined): number {
  const page = Number(value)
  return Number.isInteger(page) && page >= 1 ? page : 1
}
