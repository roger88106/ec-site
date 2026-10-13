import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { keepPreviousData } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { DateTime } from '@/components/DateTime'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDelta } from '@/lib/delta'
import { stockAdjustmentReasonLabel } from '@/lib/labels'

/** 調整の履歴（新しい順。AC-45） */
export function StockHistory({ variantId }: { variantId: string }) {
  const t = useTranslations('inventory.history')
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
        <CardTitle>{t('title') /* 調整の履歴 */}</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState query={query}>
          {(result) => (
            <div className="space-y-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('adjustedAt') /* 日時 */}</TableHead>
                    <TableHead className="text-right">{t('delta') /* 増減量 */}</TableHead>
                    <TableHead>{t('reason') /* 理由 */}</TableHead>
                    <TableHead className="text-right">{t('quantityAfter') /* 調整後の在庫数 */}</TableHead>
                    <TableHead>{t('memo') /* メモ */}</TableHead>
                    <TableHead>{t('operator') /* 操作した運営者 */}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <DateTime value={item.adjustedAt} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{formatDelta(item.delta)}</TableCell>
                      <TableCell>{stockAdjustmentReasonLabel(item.reason)}</TableCell>
                      <TableCell className="text-right tabular-nums">{item.quantityAfter}</TableCell>
                      {/* 注文に由来する調整は、メモの代わりに注文番号を出す（AC-107） */}
                      <TableCell className="whitespace-pre-wrap">
                        {
                          item.orderNumber
                            ? t('orderNumber', { orderNumber: item.orderNumber })
                            : item.memo /* 注文番号かメモ */
                        }
                      </TableCell>
                      {/* 注文に由来する調整と、記録を始める前の調整は空（AC-142） */}
                      <TableCell>{item.operatorName}</TableCell>
                    </TableRow>
                  ))}
                  {result.items.length === 0 && (
                    <TableRow>
                      <TableCell className="text-center text-muted-foreground" colSpan={6}>
                        {t('empty') /* まだない */}
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
