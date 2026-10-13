import { Badge } from '@ec-site/shared/ui/badge'
import type { Metadata } from 'next'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'
import { getMemberApi } from '@/api/client'
import { unwrapMember } from '@/api/result'
import { currentMember, redirectToLogin } from '@/auth'
import { PageLinks } from '@/components/PageLinks'
import { orderListHref, parseOrderListPage } from '@/features/order/orderListQuery'
import { formatDateTime, formatYen, orderStatusLabel } from '@/lib/orderDisplay'

/** ブラウザのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('order.list')
  return { title: t('title') } // ご注文履歴
}

/** 注文の一覧。ログインを求め、自分の注文を新しい順に出す */
export default async function OrdersPage({ searchParams }: PageProps<'/orders'>) {
  const t = await getTranslations('order.list')
  if ((await currentMember()) === null) {
    return redirectToLogin()
  }
  const page = parseOrderListPage(await searchParams)
  const api = await getMemberApi()
  const orders = await unwrapMember(await api.GET('/me/orders', { params: { query: { page } } }))

  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">{t('title') /* ご注文履歴 */}</h1>
      {orders.items.length === 0 ? (
        <div className="space-y-4">
          <p className="text-muted-foreground">{t('empty') /* まだない */}</p>
          <Link className="text-sm underline" href="/products">
            {t('findProducts') /* 商品を探す */}
          </Link>
        </div>
      ) : (
        <div className="space-y-4">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-muted-foreground">
              <tr>
                <th className="py-2 font-normal">{t('orderNumber') /* 注文番号 */}</th>
                <th className="py-2 font-normal">{t('orderedAt') /* 注文日時 */}</th>
                <th className="py-2 text-right font-normal">{t('total') /* 総額 */}</th>
                <th className="py-2 text-right font-normal">{t('status') /* 状態 */}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {orders.items.map((order) => (
                <tr key={order.id}>
                  <td className="py-3">
                    <Link className="font-medium tabular-nums underline" href={`/orders/${order.id}`}>
                      {order.orderNumber}
                    </Link>
                  </td>
                  <td className="py-3 tabular-nums">{formatDateTime(order.orderedAt)}</td>
                  <td className="py-3 text-right tabular-nums">{formatYen(order.totalAmount)}</td>
                  <td className="py-3 text-right">
                    <Badge variant={order.status === 'ACCEPTED' ? 'default' : 'secondary'}>
                      {orderStatusLabel(order.status)}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <PageLinks
            hrefFor={orderListHref}
            page={orders.page}
            totalItems={orders.totalItems}
            totalPages={orders.totalPages}
          />
        </div>
      )}
    </div>
  )
}
