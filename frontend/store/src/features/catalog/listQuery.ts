import { productSortValues } from '@/api/generated/store'
import type { ProductSort } from '@/api/types'

/** 商品一覧の絞り込み・並び順・ページ（AC-52〜54、57）。URL のクエリに持つ（店舗画面の構成 第 3 節） */
export type ListQuery = {
  categoryId: string | null
  minPrice: number | null
  maxPrice: number | null
  sort: ProductSort
  page: number
}

/** Next.js の searchParams（同じ名前が複数あれば配列） */
export type SearchParams = Record<string, string | string[] | undefined>

/** 並び順の表示名（AC-54） */
export const sortLabels: Record<ProductSort, string> = {
  newest: '新着順',
  price_asc: '価格の安い順',
  price_desc: '価格の高い順',
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
export function listHref(query: ListQuery): string {
  const params = new URLSearchParams()
  if (query.categoryId) params.set('categoryId', query.categoryId)
  if (query.minPrice !== null) params.set('minPrice', String(query.minPrice))
  if (query.maxPrice !== null) params.set('maxPrice', String(query.maxPrice))
  if (query.sort !== 'newest') params.set('sort', query.sort)
  if (query.page > 1) params.set('page', String(query.page))
  const search = params.toString()
  return search ? `/products?${search}` : '/products'
}

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

function toPage(value: string | undefined): number {
  const page = Number(value)
  return Number.isInteger(page) && page >= 1 ? page : 1
}
