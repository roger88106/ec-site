import { formatNumber, formatYen } from '@ec-site/shared/lib/format'
import { keepPreviousData } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import type { VariantSalesReport } from '@/api/types'
import { ChartValueTooltip } from '@/components/ChartValueTooltip'
import { QueryState } from '@/components/QueryState'
import { type ChartConfig, ChartContainer, ChartTooltip } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CsvButton } from '@/features/report/components/CsvButton'
import { ReportPage } from '@/features/report/components/ReportPage'
import { variantSalesLabel } from '@/features/report/labels'
import { CALENDAR_PRESETS } from '@/features/report/period'
import type { ReportCondition } from '@/features/report/reportCondition'

/** バリアントの売れ行き。商品を選んでから出す。条件は URL に持つ */
export function VariantSalesPage() {
  return (
    <ReportPage
      defaultPreset="thisMonth"
      excludeShipping={false}
      menuKey="variantSales"
      presets={CALENDAR_PRESETS}
      productRequired
    >
      {(condition) => <VariantSalesView condition={condition} />}
    </ReportPage>
  )
}

/** 商品を選んでいればグラフと表、まだなら選ぶ案内 */
function VariantSalesView({ condition }: { condition: ReportCondition }) {
  const t = useTranslations('report.variantSales')
  return condition.productId === undefined ? (
    <p className="text-muted-foreground text-sm">{t('selectProduct') /* 商品を選ぶ */}</p>
  ) : (
    <VariantSalesQuery condition={condition} productId={condition.productId} />
  )
}

/** 選んだ商品のバリアントの売れ行きを取得して出す */
function VariantSalesQuery({ condition, productId }: { condition: ReportCondition; productId: string }) {
  const query = $api.useQuery(
    'get',
    '/reports/variant-sales',
    {
      params: {
        query: {
          productId,
          from: condition.from,
          to: condition.to,
          basis: condition.basis,
          excludeTax: condition.excludeTax,
        },
      },
    },
    { placeholderData: keepPreviousData },
  )
  return (
    <QueryState query={query}>{(report) => <VariantSalesResult condition={condition} report={report} />}</QueryState>
  )
}

/** バリアントごとの棒グラフと表（販売数量の多い順） */
function VariantSalesResult({ condition, report }: { condition: ReportCondition; report: VariantSalesReport }) {
  const t = useTranslations('report.variantSales')
  const rows = report.variants.map((variant) => ({ ...variant, name: variantSalesLabel(variant) }))
  const chartConfig = { quantity: { label: t('quantity'), color: 'var(--chart-2)' } } satisfies ChartConfig // 販売数量
  return (
    <div className="space-y-6">
      <p className="font-medium">{t('product', { name: report.productName }) /* 商品名 */}</p>
      <section aria-label={t('chart') /* バリアントごとの販売数量 */}>
        <ChartContainer className="h-72 w-full" config={chartConfig}>
          <BarChart data={rows} margin={{ left: 24, right: 24 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="name" tickLine={false} />
            <YAxis allowDecimals={false} tickLine={false} />
            <ChartTooltip content={<ChartValueTooltip formatValue={formatNumber} />} />
            <Bar dataKey="quantity" isAnimationActive={false} fill="var(--color-quantity)" radius={4} />
          </BarChart>
        </ChartContainer>
      </section>
      <div className="flex justify-end">
        <CsvButton
          filename={`${t('filename')}_${report.productName}_${condition.from}_${condition.to}.csv`} /* CSV のファイル名 */
          rows={[
            [t('variant'), t('variantCode'), t('quantity'), t('amount')], // 見出しの行（バリアント、品番、販売数量、売上額）
            ...rows.map((row) => [row.name, row.variantCode, row.quantity, row.amount]),
          ]}
        />
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('variant') /* バリアント */}</TableHead>
            <TableHead>{t('variantCode') /* 品番 */}</TableHead>
            <TableHead className="text-right">{t('quantity') /* 販売数量 */}</TableHead>
            <TableHead className="text-right">{t('amount') /* 売上額 */}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.variantId}>
              <TableCell>{row.name}</TableCell>
              <TableCell>{row.variantCode}</TableCell>
              <TableCell className="text-right tabular-nums">{row.quantity}</TableCell>
              <TableCell className="text-right tabular-nums">{formatYen(row.amount)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
