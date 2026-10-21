import type { CancellationRequest, Carrier, OrderStatus, PaymentStatus, ShipmentStatus } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 注文の状態の表示名（店舗画面と同じ文言） */
export function orderStatusLabel(status: OrderStatus): string {
  return translate(`common.orderStatus.${status}`)
}

/** 注文の状態ごとの見た目（Badge の variant）。対応が必要な「キャンセル申請中」を目立たせる */
export const ORDER_STATUS_VARIANTS: Record<OrderStatus, 'default' | 'destructive' | 'secondary'> = {
  PENDING_PAYMENT: 'secondary',
  ACCEPTED: 'default',
  CANCELLATION_REQUESTED: 'destructive',
  CANCELLED: 'secondary',
  SHIPPED: 'secondary',
}

/** 支払の状態の表示名 */
export function paymentStatusLabel(status: PaymentStatus): string {
  return translate(`labels.paymentStatus.${status}`)
}

/** 支払の状態ごとの見た目（Badge の variant）。対応が必要な「与信の取消待ち」「与信期限切れ」「返金待ち」を目立たせる */
export const PAYMENT_STATUS_VARIANTS: Record<PaymentStatus, 'destructive' | 'outline' | 'secondary'> = {
  UNPAID: 'secondary',
  AUTHORIZED: 'outline',
  VOID_PENDING: 'destructive',
  VOIDED: 'secondary',
  NOT_PAID: 'secondary',
  CAPTURED: 'secondary',
  AUTHORIZATION_EXPIRED: 'destructive',
  REFUND_PENDING: 'destructive',
  REFUNDED: 'secondary',
}

/** 配送状態の表示名（店舗画面と同じ文言） */
export function shipmentStatusLabel(status: ShipmentStatus): string {
  return translate(`common.shipmentStatus.${status}`)
}

/** 配送業者の表示名（店舗画面と同じ文言） */
export function carrierLabel(carrier: Carrier): string {
  return translate(`common.carrier.${carrier}`)
}

/** キャンセルの申請の結果の表示名 */
export function cancellationRequestResultLabel(result: CancellationRequest['result']): string {
  return translate(`order.requestResult.${result}`)
}
