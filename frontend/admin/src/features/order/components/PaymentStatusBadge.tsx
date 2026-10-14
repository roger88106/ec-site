import { Badge } from '@ec-site/shared/ui/badge'
import type { PaymentStatus } from '@/api/types'
import { paymentStatusLabel } from '@/lib/labels'

/** 状態ごとの見た目。対応が要る「与信の取消待ち」「与信期限切れ」「返金待ち」を目立たせる */
const variants: Record<PaymentStatus, 'destructive' | 'outline' | 'secondary'> = {
  UNPAID: 'secondary',
  AUTHORIZED: 'outline',
  VOID_PENDING: 'destructive',
  VOIDED: 'secondary',
  NOT_PAID: 'secondary',
  CAPTURED: 'secondary',
  AUTHORIZATION_EXPIRED: 'destructive',
  REFUND_PENDING: 'destructive',
  REFUNDED: 'secondary',
}

/** 支払の状態 */
export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  return <Badge variant={variants[status]}>{paymentStatusLabel(status)}</Badge>
}
