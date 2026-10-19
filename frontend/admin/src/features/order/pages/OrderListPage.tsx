import { keepPreviousData } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { DateTime } from '@/components/DateTime'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AuthorizationExpiringBadge } from '@/features/order/components/AuthorizationExpiringBadge'
import { OrderFilterForm } from '@/features/order/components/OrderFilterForm'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { PaymentStatusBadge } from '@/features/order/components/PaymentStatusBadge'
import { type OrderFilter, parseFilter, toSearchParams } from '@/features/order/orderFilter'
import { formatYen } from '@/lib/format'
import { shipmentStatusLabel } from '@/lib/labels'

/**
 * 注文一覧。注文日時の新しい順で、キャンセル済みも含む。与信期限が近い注文には印を付ける。<br />
 * 絞り込みとページは URL のクエリに持ち、戻る・再読み込みで同じ一覧を出す。
 */
export function OrderListPage() {
  const t = useTranslations('order.list')
  const tCommon = useTranslations('common')
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseFilter(searchParams)
  const navigate = useNavigate()
  const query = $api.useQuery(
    'get',
    '/orders',
    {
      params: {
        query: {
          orderNumber: filter.orderNumber || undefined,
          status: filter.status ?? undefined,
          orderedFrom: filter.orderedFrom || undefined,
          orderedTo: filter.orderedTo || undefined,
          authorizationExpiring: filter.authorizationExpiring || undefined,
          returned: filter.returned || undefined,
          page: filter.page,
        },
      },
    },
    // ページを移る間、前のページを表示したままにする
    { placeholderData: keepPreviousData },
  )
  /** 絞り込みを URL に反映する（URL が絞り込みの正本） */
  const apply = (next: OrderFilter) => setSearchParams(toSearchParams(next))

  return (
    <>
      <PageHeader title={t('title') /* 注文 */} />
      <div className="space-y-4">
        {/* 絞り込みを変えたら（URL が変わったら）入力欄を作り直す */}
        <OrderFilterForm filter={filter} key={searchParams.toString()} onApply={apply} />
        <QueryState query={query}>
          {(result) => (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{tCommon('order.orderNumber') /* 注文番号 */}</TableHead>
                    <TableHead>{tCommon('order.orderedAt') /* 注文日時 */}</TableHead>
                    <TableHead>{t('customerName') /* 氏名 */}</TableHead>
                    <TableHead className="text-right">{t('total') /* 総額 */}</TableHead>
                    <TableHead>{t('status') /* 状態 */}</TableHead>
                    <TableHead>{t('payment') /* 支払 */}</TableHead>
                    <TableHead>{t('shipment') /* 配送 */}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.items.map((item) => (
                    <TableRow className="cursor-pointer" key={item.id} onClick={() => navigate(paths.order(item.id))}>
                      <TableCell className="font-mono">
                        {/* 行のクリックでも同じ画面へ移るので、リンクのクリックを行に伝えない（二重に移らない） */}
                        <Link onClick={(event) => event.stopPropagation()} to={paths.order(item.id)}>
                          {item.orderNumber}
                        </Link>
                      </TableCell>
                      <TableCell>
                        <DateTime value={item.orderedAt} />
                      </TableCell>
                      <TableCell>{item.customerName}</TableCell>
                      <TableCell className="text-right tabular-nums">{formatYen(item.total)}</TableCell>
                      <TableCell>
                        <OrderStatusBadge status={item.status} />
                      </TableCell>
                      <TableCell>
                        {/* 決済を入れる前の注文は、支払の状態を持たない */}
                        <div className="flex flex-wrap items-center gap-1">
                          {item.paymentStatus && <PaymentStatusBadge status={item.paymentStatus} />}
                          {item.authorizationExpiring && <AuthorizationExpiringBadge />}
                        </div>
                      </TableCell>
                      <TableCell>
                        {/* 出荷の前は、配送状態を持たない */}
                        {item.shipmentStatus && shipmentStatusLabel(item.shipmentStatus)}
                      </TableCell>
                    </TableRow>
                  ))}
                  {result.items.length === 0 && (
                    <TableRow>
                      <TableCell className="text-center text-muted-foreground" colSpan={7}>
                        {t('empty') /* 該当なし */}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Pagination
                onChange={(page) => apply({ ...filter, page })}
                page={result.page}
                totalItems={result.totalItems}
                totalPages={result.totalPages}
              />
            </>
          )}
        </QueryState>
      </div>
    </>
  )
}
