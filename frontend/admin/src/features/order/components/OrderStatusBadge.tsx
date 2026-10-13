import { Badge } from '@ec-site/shared/ui/badge'
import type { OrderStatus } from '@/api/types'
import { orderStatusLabel } from '@/lib/labels'

/** 状態ごとの見た目。対応が要る「キャンセル申請中」を目立たせる */
const variants: Record<OrderStatus, 'default' | 'destructive' | 'secondary'> = {
  PENDING_PAYMENT: 'secondary',
  ACCEPTED: 'default',
  CANCELLATION_REQUESTED: 'destructive',
  CANCELLED: 'secondary',
}

/** 注文の状態 */
export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge variant={variants[status]}>{orderStatusLabel(status)}</Badge>
}
