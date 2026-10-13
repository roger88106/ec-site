import { Badge } from '@ec-site/shared/ui/badge'
import type { PaymentStatus } from '@/api/types'
import { paymentStatusLabel } from '@/lib/labels'

/** 状態ごとの見た目。取消が済んでいない「与信の取消待ち」を目立たせる */
const variants: Record<PaymentStatus, 'destructive' | 'outline' | 'secondary'> = {
  UNPAID: 'secondary',
  AUTHORIZED: 'outline',
  VOID_PENDING: 'destructive',
  VOIDED: 'secondary',
  NOT_PAID: 'secondary',
}

/** 支払の状態 */
export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge variant={variants[status]}>{paymentStatusLabel(status)}</Badge>
}
