import type { StockAdjustmentReason } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 在庫の調整の理由の表示名 */
export function stockAdjustmentReasonLabel(reason: StockAdjustmentReason): string {
  return translate(`labels.stockAdjustmentReason.${reason}`)
}
