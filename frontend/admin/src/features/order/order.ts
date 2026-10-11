import type { CancellationRequest, OrderStatus, OrderTaxAmount } from '@/api/types'
import { formatYen } from '@/lib/format'

/** キャンセルの申請の結果の表示名（AC-100） */
const CANCELLATION_REQUEST_RESULT_LABELS: Record<CancellationRequest['result'], string> = {
  PENDING: '申請中',
  APPROVED: '承認',
  REJECTED: '却下',
}

/** 注文に行える操作。承認・却下（キャンセルの申請）、運営者の判断によるキャンセル */
export type OrderActions = { approve: boolean; reject: boolean; cancel: boolean }

/** キャンセルの申請の結果の表示名（AC-100） */
export function cancellationRequestResultLabel(result: CancellationRequest['result']): string {
  return CANCELLATION_REQUEST_RESULT_LABELS[result]
}

/**
 * 注文の状態で行える操作（AC-101〜103）。<br />
 * 承認・却下は「キャンセル申請中」だけ、キャンセルは「受付」と「キャンセル申請中」。
 * キャンセル済みへの操作は API では成功する（AC-105）が、何も変わらないので出さない。
 */
export function availableActions(status: OrderStatus): OrderActions {
  const requested = status === 'CANCELLATION_REQUESTED'
  return { approve: requested, reject: requested, cancel: status !== 'CANCELLED' }
}

/** 1 つの税率の消費税額（BR-8。例：「10% 対象 11,000 円（内消費税 1,000 円）」） */
export function taxLabel(tax: OrderTaxAmount): string {
  return `${tax.ratePercent}% 対象 ${formatYen(tax.taxableAmount)}（内消費税 ${formatYen(tax.taxAmount)}）`
}

/** 郵便番号（API はハイフンなしの 7 桁を返す。例：「100-0001」） */
export function formatPostalCode(postalCode: string): string {
  return `${postalCode.slice(0, 3)}-${postalCode.slice(3)}`
}
