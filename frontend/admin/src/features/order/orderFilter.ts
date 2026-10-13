import { orderStatusValues } from '@/api/generated/admin'
import type { OrderStatus } from '@/api/types'

/** 日付の形（yyyy-MM-dd） */
const DATE = /^\d{4}-\d{2}-\d{2}$/

/** 注文一覧の絞り込みとページ。URL のクエリに持つ（管理画面の構成 第 3 節） */
export type OrderFilter = {
  /** 注文番号（前方一致） */
  orderNumber: string
  status: OrderStatus | null
  /** 注文日の範囲（日本時間の日付。両端を含む） */
  orderedFrom: string
  orderedTo: string
  page: number
}

/** 絞り込みのない 1 ページ目 */
export const EMPTY_FILTER: OrderFilter = { orderNumber: '', status: null, orderedFrom: '', orderedTo: '', page: 1 }

/** URL のクエリから絞り込みを読む。不正な値は無視する */
export function parseFilter(params: URLSearchParams): OrderFilter {
  const status = params.get('status')
  const page = Number(params.get('page'))
  return {
    orderNumber: params.get('orderNumber') ?? '',
    status: orderStatusValues.includes(status as OrderStatus) ? (status as OrderStatus) : null,
    orderedFrom: date(params.get('orderedFrom')),
    orderedTo: date(params.get('orderedTo')),
    page: Number.isInteger(page) && page >= 1 ? page : 1,
  }
}

/** 絞り込みを URL のクエリにする。空の条件と 1 ページ目は書かない */
export function toSearchParams(filter: OrderFilter): URLSearchParams {
  const params = new URLSearchParams()
  if (filter.orderNumber) params.set('orderNumber', filter.orderNumber)
  if (filter.status) params.set('status', filter.status)
  if (filter.orderedFrom) params.set('orderedFrom', filter.orderedFrom)
  if (filter.orderedTo) params.set('orderedTo', filter.orderedTo)
  if (filter.page > 1) params.set('page', String(filter.page))
  return params
}

/** 日付の形なら、その値。そうでなければ空 */
function date(value: string | null): string {
  return value !== null && DATE.test(value) ? value : ''
}
