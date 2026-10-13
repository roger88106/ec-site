import { useTranslations } from 'next-intl'
import type { Order } from '@/api/types'
import { CancellationRequestForm } from '@/features/order/components/CancellationRequestForm'
import { cancellationState, formatDateTime } from '@/lib/orderDisplay'

/**
 * 注文のキャンセル。状況に応じて、申請のフォーム、申請中、却下（問い合わせ先）、キャンセル済みを出す。
 *
 * @param order        注文
 * @param contactEmail 問い合わせ先のメールアドレス
 */
export function CancellationSection({ order, contactEmail }: { order: Order; contactEmail: string }) {
  const t = useTranslations('order.cancellation')
  const request = order.cancellationRequest
  switch (cancellationState(order)) {
    case 'REQUESTABLE':
      return (
        <div className="space-y-3">
          <p className="text-sm">{t('requestable') /* 申請できる */}</p>
          <CancellationRequestForm orderId={order.id} />
        </div>
      )
    case 'PENDING':
      return (
        <p className="text-sm">
          {request && t('requestedAt', { date: formatDateTime(request.requestedAt) }) /* 申請の日時 */}
          {t('pending') /* 受け付けた */}
        </p>
      )
    case 'REJECTED':
      return (
        <div className="space-y-1 text-sm">
          <p>{t('rejected') /* 承認されなかった */}</p>
          <p>
            {
              t.rich('contact', {
                email: contactEmail,
                orderNumber: order.orderNumber,
                link: (chunks) => (
                  <a className="underline" href={`mailto:${contactEmail}`}>
                    {chunks}
                  </a>
                ),
              }) /* 問い合わせ先 */
            }
          </p>
        </div>
      )
    case 'CANCELLED':
      return <p className="text-sm">{t('cancelled') /* キャンセルされた */}</p>
  }
}
