import type { PublicationStatus, StockAdjustmentReason } from '@/api/types'

/** 商品の状態の表示名（AC-32） */
export const publicationStatusLabels: Record<PublicationStatus, string> = {
  UNPUBLISHED: '非公開',
  PUBLISHED: '公開',
  DISCONTINUED: '販売終了',
}

/** 在庫の調整の理由の表示名（AC-46） */
export const stockAdjustmentReasonLabels: Record<StockAdjustmentReason, string> = {
  ARRIVAL: '入荷',
  STOCKTAKE: '棚卸差異',
  DAMAGE_OR_LOSS: '破損・紛失',
  OTHER: 'その他',
}
