import { formatDateTime } from '@ec-site/shared/lib/format'
import { useTranslations } from 'next-intl'
import type { Order } from '@/api/types'
import { PayForm } from '@/features/order/components/PayForm'
import { paymentState } from '@/features/order/orderState'

/**
 * 注文の支払。支払待ちのときだけ出す。<br />
 * 支払えるなら支払の期限と「支払う」を、支払の画面から戻った直後なら「確認中」を、期限を過ぎたらまもなくキャンセルになることを示す。
 *
 * @param order    注文
 * @param returned 支払の画面から戻った直後か
 */
export function PaymentSection({
  order,
  returned,
}: {
  order: Pick<Order, 'id' | 'status' | 'paymentDeadline' | 'actions'>
  returned: boolean
}) {
  const t = useTranslations('order.payment')
  const state = paymentState(order, returned)
  if (state === 'NONE') {
    return null
  }
  const deadline = order.paymentDeadline === undefined ? '' : formatDateTime(order.paymentDeadline)
  return (
    <div className="space-y-3 text-sm">
      {state === 'PAYABLE' && <p>{t('payable', { deadline }) /* 支払の期限 */}</p>}
      {state === 'CONFIRMING' && <p role="status">{t('confirming') /* 確認中 */}</p>}
      {state === 'CLOSING' && <p>{t('closing') /* まもなくキャンセル */}</p>}
      {order.actions.pay && <PayForm orderId={order.id} />}
    </div>
  )
}
