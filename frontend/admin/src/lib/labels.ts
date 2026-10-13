import type { OrderStatus, PublicationStatus, StockAdjustmentReason } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 商品の状態の表示名（AC-32） */
export function publicationStatusLabel(status: PublicationStatus): string {
  return translate(`labels.publicationStatus.${status}`)
}

/** 在庫の調整の理由の表示名（AC-46、107） */
export function stockAdjustmentReasonLabel(reason: StockAdjustmentReason): string {
  return translate(`labels.stockAdjustmentReason.${reason}`)
}

/** 注文の状態の表示名（BR-9。店舗画面と同じ文言） */
export function orderStatusLabel(status: OrderStatus): string {
  return translate(`common.orderStatus.${status}`)
}
