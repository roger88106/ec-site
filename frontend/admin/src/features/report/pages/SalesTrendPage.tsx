import { formatYen } from '@ec-site/shared/lib/format'
import { keepPreviousData } from '@tanstack/react-query'
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import type { SalesTrendReport } from '@/api/types'
import { ChartValueTooltip } from '@/components/ChartValueTooltip'
import { QueryState } from '@/components/QueryState'
import { type ChartConfig, ChartContainer, ChartTooltip } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CsvButton } from '@/features/report/components/CsvButton'
import { ReportPage } from '@/features/report/components/ReportPage'
import { bucketLabel } from '@/features/report/labels'
import { FISCAL_PRESETS } from '@/features/report/period'
import { formatYenCompact } from '@/features/report/report'
import type { ReportCondition } from '@/features/report/reportCondition'

/** 売上の推移。期間・区切り・数え方などの条件は URL に持つ */
export function SalesTrendPage() {
  return (
    <ReportPage defaultPreset="thisFiscalYear" granularity menuKey="salesTrend" presets={FISCAL_PRESETS}>
      {(condition) => <SalesTrend condition={condition} />}
    </ReportPage>
  )
}

/** 条件で取得して、グラフと表を出す */
function SalesTrend({ condition }: { condition: ReportCondition }) {
  const query = $api.useQuery(
    'get',
    '/reports/sales-trend',
    {
      params: {
        query: {
          from: condition.from,
          to: condition.to,
          granularity: condition.granularity,
          basis: condition.basis,
          productId: condition.productId,
          excludeTax: condition.excludeTax,
          excludeShipping: condition.excludeShipping,
        },
      },
    },
    { placeholderData: keepPreviousData },
  )
  return <QueryState query={query}>{(report) => <SalesTrendResult condition={condition} report={report} />}</QueryState>
}

/** 推移のグラフと表 */
function SalesTrendResult({ condition, report }: { condition: ReportCondition; report: SalesTrendReport }) {
  const t = useTranslations('report.salesTrend')
  const rows = report.points.map((point) => ({ ...point, label: bucketLabel(point, condition.granularity) }))
  const chartConfig = { amount: { label: t('amount'), color: 'var(--chart-4)' } } satisfies ChartConfig // 売上額
  return (
    <div className="space-y-6">
      {report.shippingExcluded && (
        <p className="text-muted-foreground text-sm">{t('shippingExcluded') /* 送料なし */}</p>
      )}
      <section aria-label={t('chart') /* 売上額の推移 */}>
        <ChartContainer className="h-72 w-full" config={chartConfig}>
          <LineChart data={rows} margin={{ left: 24, right: 24 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="label" tickLine={false} />
            <YAxis tickFormatter={formatYenCompact} tickLine={false} width={72} />
            <ChartTooltip content={<ChartValueTooltip formatValue={formatYen} />} />
            <Line
              dataKey="amount"
              dot={false}
              isAnimationActive={false}
              stroke="var(--color-amount)"
              strokeWidth={2}
              type="monotone"
            />
          </LineChart>
        </ChartContainer>
      </section>
      <div className="flex justify-end">
        <CsvButton
          filename={`${t('filename')}_${condition.from}_${condition.to}.csv`} /* CSV のファイル名 */
          rows={[
            [t('bucket'), t('amount'), t('quantity')], // 見出しの行（区切り、売上額、販売数量）
            ...rows.map((row) => [row.label, row.amount, row.quantity]),
          ]}
        />
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('bucket') /* 区切り */}</TableHead>
            <TableHead className="text-right">{t('amount') /* 売上額 */}</TableHead>
            <TableHead className="text-right">{t('quantity') /* 販売数量 */}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.start}>
              <TableCell>{row.label}</TableCell>
              <TableCell className="text-right tabular-nums">{formatYen(row.amount)}</TableCell>
              <TableCell className="text-right tabular-nums">{row.quantity}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
