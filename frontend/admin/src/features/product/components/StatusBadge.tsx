import type { PublicationStatus } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { publicationStatusLabels } from '@/lib/labels'

/** 商品の状態の表示 */
export function StatusBadge({ status }: { status: PublicationStatus }) {
  const variant = status === 'PUBLISHED' ? 'default' : status === 'DISCONTINUED' ? 'destructive' : 'secondary'
  return <Badge variant={variant}>{publicationStatusLabels[status]}</Badge>
}
