import type { PublicationStatus } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 商品の状態の表示名 */
export function publicationStatusLabel(status: PublicationStatus): string {
  return translate(`labels.publicationStatus.${status}`)
}

/** 商品の状態ごとの見た目（Badge の variant） */
export const PUBLICATION_STATUS_VARIANTS: Record<PublicationStatus, 'default' | 'destructive' | 'secondary'> = {
  UNPUBLISHED: 'secondary',
  PUBLISHED: 'default',
  DISCONTINUED: 'destructive',
}
