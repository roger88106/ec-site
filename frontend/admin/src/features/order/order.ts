import type { CancellationRequest } from '@/api/types'
import { translate } from '@/i18n/translate'

/** キャンセルの申請の結果の表示名 */
export function cancellationRequestResultLabel(result: CancellationRequest['result']): string {
  return translate(`order.requestResult.${result}`)
}
