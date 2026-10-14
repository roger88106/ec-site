import type {
  Carrier,
  OrderStatus,
  PaymentStatus,
  PublicationStatus,
  ShipmentStatus,
  StockAdjustmentReason,
} from '@/api/types'
import { translate } from '@/i18n/translate'

/** 商品の状態の表示名 */
export function publicationStatusLabel(status: PublicationStatus): string {
  return translate(`labels.publicationStatus.${status}`)
}

/** 在庫の調整の理由の表示名 */
export function stockAdjustmentReasonLabel(reason: StockAdjustmentReason): string {
  return translate(`labels.stockAdjustmentReason.${reason}`)
}

/** 注文の状態の表示名（店舗画面と同じ文言） */
export function orderStatusLabel(status: OrderStatus): string {
  return translate(`common.orderStatus.${status}`)
}

/** 支払の状態の表示名 */
export function paymentStatusLabel(status: PaymentStatus): string {
  return translate(`labels.paymentStatus.${status}`)
}

/** 配送状態の表示名（店舗画面と同じ文言） */
export function shipmentStatusLabel(status: ShipmentStatus): string {
  return translate(`common.shipmentStatus.${status}`)
}

/** 配送業者の表示名（店舗画面と同じ文言） */
export function carrierLabel(carrier: Carrier): string {
  return translate(`common.carrier.${carrier}`)
}
