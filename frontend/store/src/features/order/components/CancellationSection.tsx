import type { Order } from '@/api/types'
import { CancellationRequestForm } from '@/features/order/components/CancellationRequestForm'
import { cancellationState, formatDateTime } from '@/lib/orderDisplay'

/**
 * 注文のキャンセル（AC-95、96）。状況に応じて、申請のフォーム、申請中、却下（問い合わせ先）、キャンセル済みを出す。
 *
 * @param order        注文
 * @param contactEmail 問い合わせ先のメールアドレス
 */
export function CancellationSection({ order, contactEmail }: { order: Order; contactEmail: string }) {
  const request = order.cancellationRequest
  switch (cancellationState(order)) {
    case 'REQUESTABLE':
      return (
        <div className="space-y-3">
          <p className="text-sm">ご注文のキャンセルを申請できます。申請の後、当店で確認してからキャンセルします。</p>
          <CancellationRequestForm orderId={order.id} />
        </div>
      )
    case 'PENDING':
      return (
        <p className="text-sm">
          {request && `${formatDateTime(request.requestedAt)} に`}
          キャンセルの申請を受け付けました。当店で確認してからキャンセルします。
        </p>
      )
    case 'REJECTED':
      return (
        <div className="space-y-1 text-sm">
          <p>キャンセルの申請は承認されませんでした。</p>
          <p>
            キャンセルをご希望の場合は、
            <a className="underline" href={`mailto:${contactEmail}`}>
              {contactEmail}
            </a>
            までお問い合わせください（注文番号 {order.orderNumber}）。
          </p>
        </div>
      )
    case 'CANCELLED':
      return <p className="text-sm">このご注文はキャンセルされました。</p>
  }
}
