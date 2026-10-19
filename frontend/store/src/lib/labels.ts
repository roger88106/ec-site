import type { UnpurchasableReason } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 購入できない理由の文言。数量は出さない */
export function unpurchasableReasonLabel(reason: NonNullable<UnpurchasableReason>): string {
  return translate(`labels.unpurchasableReason.${reason}`)
}
