import { axisValuesLabel, formatDateTime, formatYen } from '@ec-site/shared/lib/format'
import { buttonVariants } from '@ec-site/shared/ui/button'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import type { Order } from '@/api/types'
import { PayForm } from '@/components/PayForm'
import { PriceText } from '@/components/PriceText'
import { NewOrderForm } from '@/features/checkout/components/NewOrderForm'

/**
 * 支払待ちの注文の確認。注文ごとに、注文番号、注文日時、明細、総額、支払の期限を示し、支払へ進むか、新しく注文するか、戻るかを選ばせる。<br />
 * 「支払へ進む」は、支払える注文（`actions.pay`）にだけ出す。支払待ちのキャンセルの操作は置かない。
 *
 * @param orders 支払待ちの注文（1 件以上）
 */
export function PendingOrdersConfirmation({ orders }: { orders: Order[] }) {
  const t = useTranslations('checkout.pendingOrders')
  const tCommon = useTranslations('common')
  const tLabels = useTranslations('labels')
  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <h1 className="font-heading text-2xl font-semibold">{t('title') /* お支払い待ちのご注文があります */}</h1>
        <p className="text-sm">{t('description') /* 案内 */}</p>
      </header>

      <ul className="space-y-4">
        {orders.map((order) => (
          <li className="space-y-3 rounded-lg border p-4" key={order.id}>
            <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[8rem_1fr]">
              <dt className="text-muted-foreground">{tCommon('order.orderNumber') /* 注文番号 */}</dt>
              <dd className="font-semibold tabular-nums">{order.orderNumber}</dd>
              <dt className="text-muted-foreground">{tCommon('order.orderedAt') /* 注文日時 */}</dt>
              <dd className="tabular-nums">{formatDateTime(order.orderedAt)}</dd>
              {order.paymentDeadline && (
                <>
                  <dt className="text-muted-foreground">{t('deadlineLabel') /* 支払の期限 */}</dt>
                  <dd className="tabular-nums">{formatDateTime(order.paymentDeadline)}</dd>
                </>
              )}
            </dl>
            <ul className="divide-y rounded-md border">
              {order.lines.map((line) => (
                <li className="flex justify-between gap-4 p-3 text-sm" key={line.variantCode}>
                  <div className="space-y-1">
                    <p className="font-medium">{line.productName}</p>
                    {line.axisValues.length > 0 && (
                      <p className="text-muted-foreground">
                        {axisValuesLabel(line.axisValues, tLabels('axisSeparator') /* 軸の区切り */)}
                      </p>
                    )}
                    <PriceText price={line.unitPrice} />
                  </div>
                  <div className="text-right">
                    <p>{t('quantity', { quantity: line.quantity }) /* 数量 */}</p>
                    <p className="font-semibold tabular-nums">{formatYen(line.subtotal)}</p>
                  </div>
                </li>
              ))}
            </ul>
            <p className="text-right font-semibold tabular-nums">
              {t('total', { total: formatYen(order.amounts.total) }) /* 総額 */}
            </p>
            {order.actions.pay && (
              <PayForm
                label={t('pay') /* 支払へ進む */}
                orderId={order.id}
                pendingLabel={t('paying') /* 移っています */}
              />
            )}
          </li>
        ))}
      </ul>

      <div className="flex flex-wrap items-start gap-4">
        <NewOrderForm orderIds={orders.map((order) => order.id)} />
        <Link className={buttonVariants({ variant: 'outline' })} href="/cart">
          {t('back') /* 戻る */}
        </Link>
      </div>
    </div>
  )
}
