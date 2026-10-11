import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { getApi, UNCACHED } from '@/api/client'
import { unwrap } from '@/api/result'
import { AmountsSummary } from '@/components/AmountsSummary'
import { PriceText } from '@/components/PriceText'
import { Badge } from '@/components/ui/badge'
import { CancellationSection } from '@/features/order/components/CancellationSection'
import { axisValuesLabel, formatDateTime, formatPostalCode, formatYen, orderStatusLabel } from '@/lib/orderDisplay'
import { isUuid } from '@/lib/uuid'

/** キャンセルの申請が却下されたときに示す、問い合わせ先（AC-96。店舗画面の構成 第 6 節） */
const CONTACT_EMAIL = process.env.STORE_CONTACT_EMAIL ?? 'support@example.com'

/** ブラウザのタブの題名 */
export const metadata: Metadata = { title: 'ご注文の内容' }

/** 注文（T2506。AC-93〜96）。注文の直後の表示と、URL を知っている人の後からの照会を兼ねる */
export default async function OrderPage({ params }: PageProps<'/orders/[orderId]'>) {
  const { orderId } = await params
  if (!isUuid(orderId)) {
    notFound()
  }
  const api = await getApi()
  const order = unwrap(await api.GET('/orders/{orderId}', { params: { path: { orderId } }, ...UNCACHED }))
  const address = order.shippingAddress

  return (
    <div className="space-y-8">
      <header className="space-y-2">
        <h1 className="font-heading font-semibold text-2xl">ご注文の内容</h1>
        <p className="text-muted-foreground text-sm">
          この画面の URL を控えておくと、後からご注文の内容を確かめたり、キャンセルを申請したりできます。
        </p>
      </header>

      <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[8rem_1fr]">
        <dt className="text-muted-foreground">注文番号</dt>
        <dd className="font-semibold tabular-nums">{order.orderNumber}</dd>
        <dt className="text-muted-foreground">注文日時</dt>
        <dd className="tabular-nums">{formatDateTime(order.orderedAt)}</dd>
        <dt className="text-muted-foreground">状態</dt>
        <dd>
          <Badge variant={order.status === 'ACCEPTED' ? 'default' : 'secondary'}>
            {orderStatusLabel(order.status)}
          </Badge>
        </dd>
      </dl>

      <section className="space-y-3">
        <h2 className="font-semibold">明細</h2>
        <ul className="divide-y rounded-lg border">
          {order.lines.map((line) => (
            <li className="flex justify-between gap-4 p-4 text-sm" key={line.variantCode}>
              <div className="space-y-1">
                <p className="font-medium">{line.productName}</p>
                {line.axisValues.length > 0 && (
                  <p className="text-muted-foreground">{axisValuesLabel(line.axisValues)}</p>
                )}
                <PriceText price={line.unitPrice} />
              </div>
              <div className="text-right">
                <p>数量 {line.quantity}</p>
                <p className="font-semibold tabular-nums">{formatYen(line.subtotal)}</p>
              </div>
            </li>
          ))}
        </ul>
        <div className="rounded-lg border p-4 md:ml-auto md:w-96">
          <AmountsSummary amounts={order.amounts} />
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">お届け先</h2>
        <address className="space-y-1 text-sm not-italic">
          <p>{address.name} 様</p>
          <p>
            〒{formatPostalCode(address.postalCode)} {address.prefecture}
            {address.address}
          </p>
          <p>電話番号 {address.phone}</p>
          <p>メールアドレス {order.email}</p>
        </address>
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">キャンセル</h2>
        <CancellationSection contactEmail={CONTACT_EMAIL} order={order} />
      </section>
    </div>
  )
}
