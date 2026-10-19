import { Badge } from '@ec-site/shared/ui/badge'
import type { PublicationStatus } from '@/api/types'
import { PUBLICATION_STATUS_VARIANTS, publicationStatusLabel } from '@/features/product/labels'

/** 商品の状態の表示 */
export function StatusBadge({ status }: { status: PublicationStatus }) {
  return <Badge variant={PUBLICATION_STATUS_VARIANTS[status]}>{publicationStatusLabel(status)}</Badge>
}
