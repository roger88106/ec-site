import { publicationStatusValues } from '@/api/generated/admin'
import type { PublicationStatus } from '@/api/types'
import { pageParam } from '@/lib/searchParams'

/** 商品の一覧の絞り込みとページ。URL のクエリに持つ（管理画面の構成の「画面の URL」） */
export type ProductFilter = {
  /** 商品品番かバリアント品番（前方一致）。空なら絞り込まない */
  code: string
  /** 商品名（部分一致）。空なら絞り込まない */
  name: string
  /** 状態。null はすべて */
  status: PublicationStatus | null
  /** カテゴリ（子孫を含む）。null はすべて */
  categoryId: string | null
  /** ページ番号。1 始まり */
  page: number
}

/** 絞り込みのない 1 ページ目 */
export const EMPTY_FILTER: ProductFilter = { code: '', name: '', status: null, categoryId: null, page: 1 }

/** URL のクエリから絞り込みを読む。不正な値は無視する */
export function parseFilter(params: URLSearchParams): ProductFilter {
  const status = params.get('status')
  return {
    code: params.get('code') ?? '',
    name: params.get('name') ?? '',
    status: publicationStatusValues.includes(status as PublicationStatus) ? (status as PublicationStatus) : null,
    categoryId: params.get('categoryId'),
    page: pageParam(params),
  }
}

/** 絞り込みを URL のクエリにする。空の条件と 1 ページ目は書かない */
export function toSearchParams(filter: ProductFilter): URLSearchParams {
  const params = new URLSearchParams()
  if (filter.code) params.set('code', filter.code)
  if (filter.name) params.set('name', filter.name)
  if (filter.status) params.set('status', filter.status)
  if (filter.categoryId) params.set('categoryId', filter.categoryId)
  if (filter.page > 1) params.set('page', String(filter.page))
  return params
}
