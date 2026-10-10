import { keepPreviousData } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { $api } from '@/api/client'
import { DateTime } from '@/components/DateTime'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { OrderFilterForm } from '@/features/order/components/OrderFilterForm'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { formatYen } from '@/features/order/order'
import { type OrderFilter, parseFilter, toSearchParams } from '@/features/order/orderFilter'

/**
 * 注文一覧（T2601。AC-97〜99、106）。注文日時の新しい順で、キャンセル済みも含む。<br />
 * 絞り込みとページは URL のクエリに持ち、戻る・再読み込みで同じ一覧を出す。
 */
export function OrderListPage() {
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
      <PageHeader title="注文" />
      <div className="space-y-4">
        {/* 絞り込みを変えたら（URL が変わったら）入力欄を作り直す */}
        <OrderFilterForm filter={filter} key={searchParams.toString()} onApply={apply} />
        <QueryState query={query}>
          {(result) => (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>注文番号</TableHead>
                    <TableHead>注文日時</TableHead>
                    <TableHead>氏名</TableHead>
                    <TableHead className="text-right">総額</TableHead>
                    <TableHead>状態</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.items.map((item) => (
                    <TableRow className="cursor-pointer" key={item.id} onClick={() => navigate(`/orders/${item.id}`)}>
                      <TableCell className="font-mono">
                        <Link onClick={(event) => event.stopPropagation()} to={`/orders/${item.id}`}>
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
                    </TableRow>
                  ))}
                  {result.items.length === 0 && (
                    <TableRow>
                      <TableCell className="text-center text-muted-foreground" colSpan={5}>
                        該当する注文はありません。
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
