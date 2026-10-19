import { Badge } from '@ec-site/shared/ui/badge'
import type { PaymentStatus } from '@/api/types'
import { PAYMENT_STATUS_VARIANTS, paymentStatusLabel } from '@/features/order/labels'

/** 支払の状態 */
export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge variant={PAYMENT_STATUS_VARIANTS[status]}>{paymentStatusLabel(status)}</Badge>
}
