import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { Link, useParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { DateTime } from '@/components/DateTime'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { CancellationCard } from '@/features/order/components/CancellationCard'
import { OrderActions } from '@/features/order/components/OrderActions'
import { OrderLinesCard } from '@/features/order/components/OrderLinesCard'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { PaymentStatusBadge } from '@/features/order/components/PaymentStatusBadge'
import { ShipmentCard } from '@/features/order/components/ShipmentCard'
import { formatPostalCode } from '@/features/order/order'

/** 注文の詳細と、出荷の登録、承認・却下・キャンセルの操作 */
export function OrderDetailPage() {
  const t = useTranslations('order.detail')
  const tCommon = useTranslations('common')
  const { orderId = '' } = useParams()
  const query = $api.useQuery('get', '/orders/{orderId}', { params: { path: { orderId } } })

  return (
    <>
      <PageHeader
        title={
          <span>
            {t('titlePrefix') /* 注文 */}
            <span className="font-mono">{query.data?.orderNumber ?? '…'}</span>
          </span>
        }
      >
        <Button
          nativeButton={false} /* a を描くので、button の振る舞いを外す */
          render={<Link to={paths.orders()} />}
          variant="outline"
        >
          {tCommon('backToList') /* 一覧へ戻る */}
        </Button>
      </PageHeader>
      <QueryState query={query}>
        {(order) => (
          <div className="max-w-5xl space-y-6">
            <dl className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
              <div className="flex items-center gap-2">
                <dt className="text-muted-foreground">{t('status') /* 状態 */}</dt>
                <dd>
                  <OrderStatusBadge status={order.status} />
                </dd>
              </div>
              <div className="flex items-center gap-2">
                <dt className="text-muted-foreground">{t('orderedAt') /* 注文日時 */}</dt>
                <dd>
                  <DateTime value={order.orderedAt} />
                </dd>
              </div>
              {/* 決済を入れる前の注文は、支払を持たない */}
              {order.payment && (
                <>
                  <div className="flex items-center gap-2">
                    <dt className="text-muted-foreground">{t('payment') /* 支払 */}</dt>
                    <dd>
                      <PaymentStatusBadge status={order.payment.status} />
                    </dd>
                  </div>
                  <div className="flex items-center gap-2">
                    <dt className="text-muted-foreground">{t('paymentDeadline') /* 支払の期限 */}</dt>
                    <dd>
                      <DateTime value={order.payment.deadline} />
                    </dd>
                  </div>
                </>
              )}
            </dl>
            <OrderActions order={order} />
            <ShipmentCard order={order} />
            <OrderLinesCard amounts={order.amounts} lines={order.lines} />
            <Card>
              <CardHeader>
                <CardTitle>{t('customer') /* お客様とお届け先 */}</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="grid grid-cols-[7rem_1fr] gap-y-1 text-sm">
                  <dt className="text-muted-foreground">{t('email') /* メールアドレス */}</dt>
                  <dd>{order.email}</dd>
                  <dt className="text-muted-foreground">{t('name') /* 氏名 */}</dt>
                  <dd>{order.shippingAddress.name}</dd>
                  <dt className="text-muted-foreground">{t('address') /* 住所 */}</dt>
                  <dd>
                    {
                      t('postalAddress', {
                        postalCode: formatPostalCode(order.shippingAddress.postalCode),
                        prefecture: order.shippingAddress.prefecture,
                      }) /* 郵便番号と都道府県 */
                    }
                    {order.shippingAddress.address}
                  </dd>
                  <dt className="text-muted-foreground">{t('phone') /* 電話番号 */}</dt>
                  <dd className="tabular-nums">{order.shippingAddress.phone}</dd>
                </dl>
              </CardContent>
            </Card>
            <CancellationCard cancellation={order.cancellation} request={order.cancellationRequest} />
          </div>
        )}
      </QueryState>
    </>
  )
}
