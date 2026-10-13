import type { CancellationRequest, OrderTaxAmount } from '@/api/types'
import { translate } from '@/i18n/translate'
import { formatYen } from '@/lib/format'

/** キャンセルの申請の結果の表示名（AC-100） */
export function cancellationRequestResultLabel(result: CancellationRequest['result']): string {
  return translate(`order.requestResult.${result}`)
}

/** 1 つの税率の消費税額（BR-8。例：「10% 対象 11,000 円（内消費税 1,000 円）」） */
export function taxLabel(tax: OrderTaxAmount): string {
  return translate('common.taxAmount', {
    ratePercent: tax.ratePercent,
    taxableAmount: formatYen(tax.taxableAmount),
    taxAmount: formatYen(tax.taxAmount),
  })
}

/** 郵便番号（API はハイフンなしの 7 桁を返す。例：「100-0001」） */
export function formatPostalCode(postalCode: string): string {
  return `${postalCode.slice(0, 3)}-${postalCode.slice(3)}`
}
