import { orderStatusLabel, shipmentStatusLabel } from '@ec-site/shared/lib/orderLabels'
import type { OrderStatus, ShipmentStatus } from '@/api/types'

/** 注文の状態の見た目（Badge の variant）。受け付けた注文を目立たせる */
const ORDER_STATUS_VARIANTS: Record<OrderStatus, 'default' | 'secondary'> = {
  PENDING_PAYMENT: 'secondary',
  ACCEPTED: 'default',
  CANCELLATION_REQUESTED: 'secondary',
  CANCELLED: 'secondary',
  SHIPPED: 'secondary',
}

/**
 * 注文の状態の欄に出す表示名と見た目。出荷済みの注文は、注文状態の代わりに配送状態を出す
 * （注文状態と配送状態の「出荷済み」が並ばないようにする）。
 */
export function orderStatusBadge(order: { status: OrderStatus; shipmentStatus?: ShipmentStatus }): {
  label: string
  variant: 'default' | 'secondary'
} {
  if (order.status === 'SHIPPED' && order.shipmentStatus !== undefined) {
    return { label: shipmentStatusLabel(order.shipmentStatus), variant: ORDER_STATUS_VARIANTS.SHIPPED }
  }
  return { label: orderStatusLabel(order.status), variant: ORDER_STATUS_VARIANTS[order.status] }
}
