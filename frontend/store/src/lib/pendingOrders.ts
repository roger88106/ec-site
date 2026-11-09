import type { Route } from 'next'
import type { ErrorCode } from '@/api/types'

/** 注文手続きに進む前に、支払待ちの注文の確認が必要なときのエラーコード（409） */
export const PENDING_ORDER_CONFIRMATION_REQUIRED: ErrorCode = 'PENDING_ORDER_CONFIRMATION_REQUIRED'

/** 支払待ちの注文の確認の画面 */
export const PENDING_ORDERS_PATH = '/checkout/pending-orders'

/** 支払待ちの注文が上限に達しているため、注文できないときのエラーコード（409） */
export const PENDING_ORDER_LIMIT_EXCEEDED: ErrorCode = 'PENDING_ORDER_LIMIT_EXCEEDED'

/** 上限に達したことを示すクエリパラメーターの名前 */
export const LIMIT_PARAM = 'limit'

/** 上限に達したことを示すクエリパラメーターの値 */
export const LIMIT_REACHED = 'reached'

/** 上限に達したことを示す確認の画面（クエリ付き） */
export const PENDING_ORDERS_LIMIT_PATH: Route = pendingOrdersLimitHref()

// ============================== プライベート ==============================

/** 上限に達したことを示すクエリを付けた、確認の画面の URL */
function pendingOrdersLimitHref(): Route {
  const params = new URLSearchParams({ [LIMIT_PARAM]: LIMIT_REACHED })
  return `${PENDING_ORDERS_PATH}?${params.toString()}` as Route
}
