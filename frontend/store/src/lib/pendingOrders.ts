import type { Route } from 'next'
import type { ErrorCode } from '@/api/types'
import type { SearchParams } from '@/lib/searchParams'

/** 注文手続きに進む前に、支払待ちの注文の確認が必要なときのエラーコード */
export const PENDING_ORDER_CONFIRMATION_REQUIRED: ErrorCode = 'PENDING_ORDER_CONFIRMATION_REQUIRED'

/** 支払待ちの注文の確認の画面 */
export const PENDING_ORDERS_PATH = '/checkout/pending-orders'

/** 支払待ちの注文が上限に達しているため、注文できないときのエラーコード */
export const PENDING_ORDER_LIMIT_EXCEEDED: ErrorCode = 'PENDING_ORDER_LIMIT_EXCEEDED'

/** 上限に達したことを示すクエリパラメーターの名前 */
const LIMIT_PARAM = 'limit'

/** 上限に達したことを示すクエリパラメーターの値 */
const LIMIT_REACHED = 'reached'

/**
 * 上限に達したことを示す確認の画面の URL（クエリ付き）を返す。
 *
 * `actions/` は `features/` を import できないので、`lib/` に置く。
 *
 * @returns 上限に達したことを示すクエリを付けた、確認の画面の URL
 */
export function pendingOrdersLimitHref(): Route {
  const params = new URLSearchParams({ [LIMIT_PARAM]: LIMIT_REACHED })
  return `${PENDING_ORDERS_PATH}?${params.toString()}`
}

/**
 * 確認の画面のクエリが、上限に達したことを示すかを読む。
 *
 * @param params 画面のクエリ
 * @returns `limit=reached` なら true。ない・他の値・同じ名前が複数なら false
 */
export function isLimitReached(params: SearchParams): boolean {
  return params[LIMIT_PARAM] === LIMIT_REACHED
}
