import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { useTranslations } from 'use-intl'
import type { OrderAmounts, OrderLine } from '@/api/types'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { taxLabel } from '@/features/order/order'
import { formatYen } from '@/lib/format'

/**
 * 明細と金額の内訳。明細は注文時点の写しで、今の商品の内容ではない。
 */
export function OrderLinesCard({ lines, amounts }: { lines: OrderLine[]; amounts: OrderAmounts }) {
  const t = useTranslations('order.lines')
  const tCommon = useTranslations('common')
  const tApp = useTranslations('app.common')
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 明細 */}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('code') /* 品番 */}</TableHead>
              <TableHead>{t('productName') /* 商品名 */}</TableHead>
              <TableHead className="text-right">{t('unitPriceExclTax') /* 税抜単価 */}</TableHead>
              <TableHead className="text-right">{t('ratePercent') /* 税率 */}</TableHead>
              <TableHead className="text-right">{t('unitPriceInclTax') /* 税込単価 */}</TableHead>
              <TableHead className="text-right">{t('quantity') /* 数量 */}</TableHead>
              <TableHead className="text-right">{t('subtotal') /* 小計 */}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {lines.map((line) => (
              <TableRow key={line.variantId}>
                <TableCell className="font-mono">
                  <div>{line.productCode}</div>
                  <div className="text-muted-foreground">{line.variantCode}</div>
                </TableCell>
                <TableCell>
                  <div>{line.productName}</div>
                  {line.axisValues.length > 0 && (
                    <div className="text-muted-foreground">
                      {
                        line.axisValues
                          .map((axis) => tCommon('axisValue', axis))
                          .join(tApp('listSeparator')) /* 軸の名前と値 */
                      }
                    </div>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatYen(line.unitPriceExclTax)}</TableCell>
                <TableCell className="text-right tabular-nums">{line.ratePercent}%</TableCell>
                <TableCell className="text-right tabular-nums">{formatYen(line.unitPriceInclTax)}</TableCell>
                <TableCell className="text-right tabular-nums">{line.quantity}</TableCell>
                <TableCell className="text-right tabular-nums">{formatYen(line.subtotal)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <dl className="ml-auto w-full max-w-sm space-y-1 text-sm">
          <div className="flex justify-between">
            <dt>{tCommon('amounts.itemsTotal') /* 商品の合計 */}</dt>
            <dd className="tabular-nums">{formatYen(amounts.itemsTotal)}</dd>
          </div>
          <div className="flex justify-between">
            <dt>{tCommon('amounts.shippingFee') /* 送料 */}</dt>
            <dd className="tabular-nums">{formatYen(amounts.shippingFee)}</dd>
          </div>
          <div className="flex justify-between border-t pt-1 font-semibold">
            <dt>{tCommon('amounts.total') /* 総額 */}</dt>
            <dd className="tabular-nums">{formatYen(amounts.total)}</dd>
          </div>
          {amounts.taxes.map((tax) => (
            <div className="text-right text-muted-foreground tabular-nums" key={tax.ratePercent}>
              <dt className="sr-only">
                {tCommon('amounts.taxOf', { ratePercent: tax.ratePercent }) /* 税率ごとの消費税 */}
              </dt>
              <dd>{taxLabel(tax)}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}
