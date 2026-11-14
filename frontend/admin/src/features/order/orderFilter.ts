import { orderStatusValues } from '@/api/generated/admin'
import type { OrderStatus } from '@/api/types'
import { isDateString } from '@/lib/date'
import { enumParam, pageParam, writeParams } from '@/lib/searchParams'

/**
 * 注文一覧の絞り込みとページ
 *
 * URL のクエリに持つ（管理画面の構成の「画面の URL」）。
 */
export type OrderFilter = {
  /** 注文番号（前方一致） */
  orderNumber: string
  /** 状態。null はすべて */
  status: OrderStatus | null
  /** 注文日の範囲（日本時間の日付。両端を含む） */
  orderedFrom: string
  orderedTo: string
  /** 与信期限が近い注文だけ */
  authorizationExpiring: boolean
  /** 配送状態が返送の注文だけ */
  returned: boolean
  /** ページ番号。1 始まり */
  page: number
}

/** 絞り込みのない 1 ページ目 */
export const EMPTY_FILTER: OrderFilter = {
  orderNumber: '',
  status: null,
  orderedFrom: '',
  orderedTo: '',
  authorizationExpiring: false,
  returned: false,
  page: 1,
}

/**
 * URL のクエリから絞り込みを読む。
 *
 * 不正な値は無視する。
 *
 * @param params URL のクエリ
 * @returns 絞り込み
 */
export function parseFilter(params: URLSearchParams): OrderFilter {
  const status = params.get('status')
  return {
    orderNumber: params.get('orderNumber') ?? '',
    status: enumParam(orderStatusValues, status, null),
    orderedFrom: date(params.get('orderedFrom')),
    orderedTo: date(params.get('orderedTo')),
    authorizationExpiring: params.get('authorizationExpiring') === 'true',
    returned: params.get('returned') === 'true',
    page: pageParam(params),
  }
}

/**
 * 絞り込みを URL のクエリにする。
 *
 * 空の条件、立っていないフラグ、1 ページ目は書かない。
 *
 * @param filter 絞り込み
 * @returns URL のクエリ
 */
export function toSearchParams(filter: OrderFilter): URLSearchParams {
  return writeParams(filter, { page: EMPTY_FILTER.page })
}

/** 日付の形ならその値、そうでなければ空にする。 */
function date(value: string | null): string {
  return value !== null && isDateString(value) ? value : ''
}
