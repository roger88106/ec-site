import type { OrderStatus } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { orderStatusLabels } from '@/lib/labels'

/** 状態ごとの見た目。対応が要る「キャンセル申請中」を目立たせる */
const variants: Record<OrderStatus, 'default' | 'destructive' | 'secondary'> = {
  ACCEPTED: 'default',
  CANCELLATION_REQUESTED: 'destructive',
  CANCELLED: 'secondary',
}

/** 注文の状態（BR-9） */
export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge variant={variants[status]}>{orderStatusLabels[status]}</Badge>
}
