import type { UnpurchasableReason } from '@/api/types'
import { translate } from '@/i18n/translate'

/**
 * 購入できない理由の文言を返す。
 *
 * 数量は出さない。
 *
 * @param reason 購入できない理由
 * @returns 文言
 */
export function unpurchasableReasonLabel(reason: NonNullable<UnpurchasableReason>): string {
  return translate(`labels.unpurchasableReason.${reason}`)
}
