import { keepPreviousData } from '@tanstack/react-query'
import { useState } from 'react'
import { $api } from '@/api/client'
import { DateTime } from '@/components/DateTime'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDelta } from '@/lib/delta'
import { stockAdjustmentReasonLabels } from '@/lib/labels'

/** 調整の履歴（新しい順。AC-45） */
export function StockHistory({ variantId }: { variantId: string }) {
  const [page, setPage] = useState(1)
  const query = $api.useQuery(
    'get',
    '/inventory/variants/{variantId}/adjustments',
    { params: { path: { variantId }, query: { page } } },
    { placeholderData: keepPreviousData },
  )
  return (
    <Card>
      <CardHeader>
        <CardTitle>調整の履歴</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState query={query}>
          {(result) => (
            <div className="space-y-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>日時</TableHead>
                    <TableHead className="text-right">増減量</TableHead>
                    <TableHead>理由</TableHead>
                    <TableHead className="text-right">調整後の在庫数</TableHead>
                    <TableHead>メモ</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <DateTime value={item.adjustedAt} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{formatDelta(item.delta)}</TableCell>
                      <TableCell>{stockAdjustmentReasonLabels[item.reason]}</TableCell>
                      <TableCell className="text-right tabular-nums">{item.quantityAfter}</TableCell>
                      {/* 注文に由来する調整は、メモの代わりに注文番号を出す（AC-107） */}
                      <TableCell className="whitespace-pre-wrap">
                        {item.orderNumber ? `注文番号 ${item.orderNumber}` : item.memo}
                      </TableCell>
                    </TableRow>
                  ))}
                  {result.items.length === 0 && (
                    <TableRow>
                      <TableCell className="text-center text-muted-foreground" colSpan={5}>
                        調整はまだありません。
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Pagination
                onChange={setPage}
                page={result.page}
                totalItems={result.totalItems}
                totalPages={result.totalPages}
              />
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}
