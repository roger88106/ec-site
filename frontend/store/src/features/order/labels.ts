import type { Carrier, OrderStatus, ShipmentStatus } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 注文の状態の表示名 */
export function orderStatusLabel(status: OrderStatus): string {
  return translate(`common.orderStatus.${status}`)
}

/** 注文の状態の見た目（Badge の variant）。受け付けた注文を目立たせる */
const ORDER_STATUS_VARIANTS: Record<OrderStatus, 'default' | 'secondary'> = {
  PENDING_PAYMENT: 'secondary',
  ACCEPTED: 'default',
  CANCELLATION_REQUESTED: 'secondary',
  CANCELLED: 'secondary',
  SHIPPED: 'secondary',
}

/** 配送状態の表示名 */
export function shipmentStatusLabel(status: ShipmentStatus): string {
  return translate(`common.shipmentStatus.${status}`)
}

/** 配送業者の表示名 */
export function carrierLabel(carrier: Carrier): string {
  return translate(`common.carrier.${carrier}`)
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
