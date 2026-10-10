import { Link, useParams } from 'react-router'
import { $api } from '@/api/client'
import { DateTime } from '@/components/DateTime'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CancellationCard } from '@/features/order/components/CancellationCard'
import { OrderLinesCard } from '@/features/order/components/OrderLinesCard'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { formatPostalCode } from '@/features/order/order'

/** 注文の詳細（T2602。AC-100、106） */
export function OrderDetailPage() {
  const { orderId = '' } = useParams()
  const query = $api.useQuery('get', '/orders/{orderId}', { params: { path: { orderId } } })

  return (
    <>
      <PageHeader
        title={
          <span>
            注文：<span className="font-mono">{query.data?.orderNumber ?? '…'}</span>
          </span>
        }
      >
        <Button nativeButton={false} render={<Link to="/orders" />} variant="outline">
          一覧へ戻る
        </Button>
      </PageHeader>
      <QueryState query={query}>
        {(order) => (
          <div className="max-w-5xl space-y-6">
            <dl className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
              <div className="flex items-center gap-2">
                <dt className="text-muted-foreground">状態</dt>
                <dd>
                  <OrderStatusBadge status={order.status} />
                </dd>
              </div>
              <div className="flex items-center gap-2">
                <dt className="text-muted-foreground">注文日時</dt>
                <dd>
                  <DateTime value={order.orderedAt} />
                </dd>
              </div>
            </dl>
            <OrderLinesCard amounts={order.amounts} lines={order.lines} />
            <Card>
              <CardHeader>
                <CardTitle>お客様とお届け先</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="grid grid-cols-[7rem_1fr] gap-y-1 text-sm">
                  <dt className="text-muted-foreground">メールアドレス</dt>
                  <dd>{order.email}</dd>
                  <dt className="text-muted-foreground">氏名</dt>
                  <dd>{order.shippingAddress.name}</dd>
                  <dt className="text-muted-foreground">住所</dt>
                  <dd>
                    〒{formatPostalCode(order.shippingAddress.postalCode)} {order.shippingAddress.prefecture}
                    {order.shippingAddress.address}
                  </dd>
                  <dt className="text-muted-foreground">電話番号</dt>
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
