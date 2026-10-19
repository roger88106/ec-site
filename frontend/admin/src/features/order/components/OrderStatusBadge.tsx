import { Badge } from '@ec-site/shared/ui/badge'
import type { OrderStatus } from '@/api/types'
import { ORDER_STATUS_VARIANTS, orderStatusLabel } from '@/features/order/labels'

/** 注文の状態 */
export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge variant={ORDER_STATUS_VARIANTS[status]}>{orderStatusLabel(status)}</Badge>
}
