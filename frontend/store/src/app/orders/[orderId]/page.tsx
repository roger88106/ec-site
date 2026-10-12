import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { getApi, getMemberApi } from '@/api/client'
import { unwrap, unwrapMember } from '@/api/result'
import { currentMember, redirectToLogin } from '@/auth'
import { AmountsSummary } from '@/components/AmountsSummary'
import { PriceText } from '@/components/PriceText'
import { Badge } from '@/components/ui/badge'
import { CancellationSection } from '@/features/order/components/CancellationSection'
import { axisValuesLabel, formatDateTime, formatPostalCode, formatYen, orderStatusLabel } from '@/lib/orderDisplay'
import { isUuid } from '@/lib/uuid'

/** ブラウザのタブの題名 */
export const metadata: Metadata = { title: 'ご注文の内容' }

/**
 * 注文（T2506、T3408。AC-93〜96、130、131）。注文の直後の表示と、注文の一覧からの照会を兼ねる。<br />
 * ログインを求め、注文した会員のものでなければ「見つかりません」にする（AC-131）。
 */
export default async function OrderPage({ params }: PageProps<'/orders/[orderId]'>) {
  const { orderId } = await params
  if ((await currentMember()) === null) {
    return redirectToLogin()
  }
  if (!isUuid(orderId)) {
    notFound()
  }
  // 店舗の情報（キャンセルの申請が却下されたときに示す問い合わせ先。AC-96）は設定の値なので、公開の口でキャッシュを使う
  const [orderResult, shopResult] = await Promise.all([
    (await getMemberApi()).GET('/orders/{orderId}', { params: { path: { orderId } } }),
    (await getApi()).GET('/shop'),
  ])
  const order = await unwrapMember(orderResult)
  const shop = unwrap(shopResult)
  const address = order.shippingAddress

  return (
    <div className="space-y-8">
      <header className="space-y-2">
        <h1 className="font-heading font-semibold text-2xl">ご注文の内容</h1>
        <p className="text-muted-foreground text-sm">
          ご注文の内容とキャンセルの申請は、
          <Link className="underline" href="/orders">
            ご注文履歴
          </Link>
          からいつでも確かめられます。
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
        <CancellationSection contactEmail={shop.contactEmail} order={order} />
      </section>
    </div>
  )
}
