import type { CancellationRequest, OrderTaxAmount } from '@/api/types'
import { formatYen } from '@/lib/format'

/** キャンセルの申請の結果の表示名（AC-100） */
const CANCELLATION_REQUEST_RESULT_LABELS: Record<CancellationRequest['result'], string> = {
  PENDING: '申請中',
  APPROVED: '承認',
  REJECTED: '却下',
}

/** キャンセルの申請の結果の表示名（AC-100） */
export function cancellationRequestResultLabel(result: CancellationRequest['result']): string {
  return CANCELLATION_REQUEST_RESULT_LABELS[result]
}

/** 1 つの税率の消費税額（BR-8。例：「10% 対象 11,000 円（内消費税 1,000 円）」） */
export function taxLabel(tax: OrderTaxAmount): string {
  return `${tax.ratePercent}% 対象 ${formatYen(tax.taxableAmount)}（内消費税 ${formatYen(tax.taxAmount)}）`
}

/** 郵便番号（API はハイフンなしの 7 桁を返す。例：「100-0001」） */
export function formatPostalCode(postalCode: string): string {
  return `${postalCode.slice(0, 3)}-${postalCode.slice(3)}`
}
