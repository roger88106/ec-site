import type { ErrorCode } from '@/api/types'

/** 注文手続きに進む前に、支払待ちの注文の確認が必要なときのエラーコード（409） */
export const PENDING_ORDER_CONFIRMATION_REQUIRED: ErrorCode = 'PENDING_ORDER_CONFIRMATION_REQUIRED'

/** 支払待ちの注文の確認の画面 */
export const PENDING_ORDERS_PATH = '/checkout/pending-orders'
