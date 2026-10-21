import { Badge } from '@ec-site/shared/ui/badge'
import type { OrderStatus, ShipmentStatus } from '@/api/types'
import { orderStatusBadge } from '@/features/order/labels'

/** 注文の状態。出荷済みの注文は、配送状態を出す */
export function OrderStatusBadge({ status, shipmentStatus }: { status: OrderStatus; shipmentStatus?: ShipmentStatus }) {
  const badge = orderStatusBadge({ status, shipmentStatus })
  return <Badge variant={badge.variant}>{badge.label}</Badge>
}
