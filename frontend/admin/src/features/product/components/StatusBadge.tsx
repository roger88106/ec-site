import { Badge } from '@ec-site/shared/ui/badge'
import type { PublicationStatus } from '@/api/types'
import { publicationStatusLabel } from '@/lib/labels'

/** 状態ごとの見た目 */
const variants: Record<PublicationStatus, 'default' | 'destructive' | 'secondary'> = {
  UNPUBLISHED: 'secondary',
  PUBLISHED: 'default',
  DISCONTINUED: 'destructive',
}

/** 商品の状態の表示 */
export function StatusBadge({ status }: { status: PublicationStatus }) {
  return <Badge variant={variants[status]}>{publicationStatusLabel(status)}</Badge>
}
