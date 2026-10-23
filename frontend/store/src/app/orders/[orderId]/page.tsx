import { AmountsSummary } from '@ec-site/shared/components/AmountsSummary'
import { axisValuesLabel, formatDateTime, formatYen } from '@ec-site/shared/lib/format'
import { carrierLabel } from '@ec-site/shared/lib/orderLabels'
import { isUuid } from '@ec-site/shared/lib/patterns'
import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { getTranslations } from 'next-intl/server'
import { getApi, getMemberApi } from '@/api/client'
import { unwrap, unwrapMember } from '@/api/result'
import { currentMember, redirectToLogin } from '@/auth'
import { PriceText } from '@/components/PriceText'
import { CancellationSection } from '@/features/order/components/CancellationSection'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { PaymentSection } from '@/features/order/components/PaymentSection'
import { ShippingAddressDetails } from '@/features/order/components/ShippingAddressDetails'
import {
  cancellationState,
  paymentState,
  showsCancellationSection,
  showsPaymentSection,
} from '@/features/order/orderState'

/** ブラウザのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('order.detail')
  return { title: t('title') } // ご注文の内容
}

/**
 * 注文。注文の直後の表示と、注文の一覧からの照会と、支払画面から戻る先を兼ねる。<br />
 * ログインを求め、注文した会員のものでなければ「見つかりません」にする。
 * 支払画面から戻るときは、クエリの `payment=returned` が付く（バックエンドの設定の戻る先）。
 */
export default async function OrderPage({ params, searchParams }: PageProps<'/orders/[orderId]'>) {
  const t = await getTranslations('order.detail')
  const tCommon = await getTranslations('common')
  const tLabels = await getTranslations('labels')
  const { orderId } = await params
  const returned = (await searchParams).payment === 'returned'
  if ((await currentMember()) === null) {
    return redirectToLogin()
  }
  if (!isUuid(orderId)) {
    notFound()
  }
  // 店舗の情報（キャンセルの申請が却下されたときに示す問い合わせ先）は設定の値なので、公開の口でキャッシュを使う
  const [orderResult, shopResult] = await Promise.all([
    (await getMemberApi()).GET('/orders/{orderId}', { params: { path: { orderId } } }),
    (await getApi()).GET('/shop'),
  ])
  const order = await unwrapMember(orderResult)
  const shop = unwrap(shopResult)

  return (
    <div className="space-y-8">
      <header className="space-y-2">
        <h1 className="font-heading font-semibold text-2xl">{t('title') /* ご注文の内容 */}</h1>
        <p className="text-muted-foreground text-sm">
          {
            t.rich('ordersHint', {
              link: (chunks) => (
                <Link className="underline" href="/orders">
                  {chunks}
                </Link>
              ),
            }) /* ご注文履歴から確認できる */
          }
        </p>
      </header>

      <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[8rem_1fr]">
        <dt className="text-muted-foreground">{tCommon('order.orderNumber') /* 注文番号 */}</dt>
        <dd className="font-semibold tabular-nums">{order.orderNumber}</dd>
        <dt className="text-muted-foreground">{tCommon('order.orderedAt') /* 注文日時 */}</dt>
        <dd className="tabular-nums">{formatDateTime(order.orderedAt)}</dd>
        <dt className="text-muted-foreground">{t('status') /* 状態 */}</dt>
        <dd>
          <OrderStatusBadge shipmentStatus={order.shipment?.status} status={order.status} />
        </dd>
        {order.shipment && (
          <>
            <dt className="text-muted-foreground">{tCommon('order.carrier') /* 配送業者 */}</dt>
            <dd>{carrierLabel(order.shipment.carrier)}</dd>
            <dt className="text-muted-foreground">{tCommon('order.trackingNumber') /* 送り状番号 */}</dt>
            <dd className="tabular-nums">{order.shipment.trackingNumber}</dd>
          </>
        )}
      </dl>

      {showsPaymentSection(paymentState(order, returned)) && (
        <section className="space-y-3">
          <h2 className="font-semibold">{t('payment') /* お支払い */}</h2>
          <PaymentSection order={order} returned={returned} />
        </section>
      )}

      <section className="space-y-3">
        <h2 className="font-semibold">{t('lines') /* 明細 */}</h2>
        <ul className="divide-y rounded-lg border">
          {order.lines.map((line) => (
            <li className="flex justify-between gap-4 p-4 text-sm" key={line.variantCode}>
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
        <div className="rounded-lg border p-4 md:ml-auto md:w-96">
          <AmountsSummary amounts={order.amounts} variant="page" />
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">{t('shippingAddress') /* お届け先 */}</h2>
        <ShippingAddressDetails address={order.shippingAddress} email={order.email} />
      </section>

      {showsCancellationSection(cancellationState(order)) && (
        <section className="space-y-3">
          <h2 className="font-semibold">{t('cancellation') /* キャンセル */}</h2>
          <CancellationSection contactEmail={shop.contactEmail} order={order} />
        </section>
      )}
    </div>
  )
}
