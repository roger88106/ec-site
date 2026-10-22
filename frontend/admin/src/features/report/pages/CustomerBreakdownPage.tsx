import { formatYen } from '@ec-site/shared/lib/format'
import { keepPreviousData } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import type { CustomerBreakdownReport } from '@/api/types'
import { ChartValueTooltip } from '@/components/ChartValueTooltip'
import { QueryState } from '@/components/QueryState'
import { type ChartConfig, ChartContainer, ChartTooltip } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CsvButton } from '@/features/report/components/CsvButton'
import { ReportPage } from '@/features/report/components/ReportPage'
import { ageGroupLabel, prefectureLabel, reportGenderLabel } from '@/features/report/labels'
import { CALENDAR_PRESETS } from '@/features/report/period'
import { formatYenCompact } from '@/features/report/report'
import type { ReportCondition } from '@/features/report/reportCondition'

/** 地域・年齢層・性別。条件は URL に持つ */
export function CustomerBreakdownPage() {
  return (
    <ReportPage defaultPreset="thisMonth" menuKey="customerBreakdown" presets={CALENDAR_PRESETS}>
      {(condition) => <CustomerBreakdown condition={condition} />}
    </ReportPage>
  )
}

/** 条件で取得して、3 つの分け方のグラフと表を出す */
function CustomerBreakdown({ condition }: { condition: ReportCondition }) {
  const query = $api.useQuery(
    'get',
    '/reports/customer-breakdown',
    {
      params: {
        query: {
          from: condition.from,
          to: condition.to,
          basis: condition.basis,
          productId: condition.productId,
          excludeTax: condition.excludeTax,
          excludeShipping: condition.excludeShipping,
        },
      },
    },
    { placeholderData: keepPreviousData },
  )
  return <QueryState query={query}>{(report) => <BreakdownResult condition={condition} report={report} />}</QueryState>
}

/** 1 つの分け方の行 */
type SegmentRow = { segment: string; amount: number; orders: number }

/** 3 つの分け方のグラフと表。CSV は 3 つの表を 1 つにまとめる */
function BreakdownResult({ condition, report }: { condition: ReportCondition; report: CustomerBreakdownReport }) {
  const t = useTranslations('report.customerBreakdown')
  const dimensions = [
    {
      key: 'prefecture',
      name: t('prefecture'), // 都道府県
      rows: report.prefectures.map((row) => ({ ...row, segment: prefectureLabel(row.prefectureName) })),
    },
    {
      key: 'ageGroup',
      name: t('ageGroup'), // 年齢層
      rows: report.ageGroups.map((row) => ({ ...row, segment: ageGroupLabel(row.ageGroup) })),
    },
    {
      key: 'gender',
      name: t('gender'), // 性別
      rows: report.genders.map((row) => ({ ...row, segment: reportGenderLabel(row.gender) })),
    },
  ] satisfies { key: string; name: string; rows: SegmentRow[] }[]
  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between gap-4">
        {report.shippingExcluded ? (
          <p className="text-muted-foreground text-sm">{t('shippingExcluded') /* 送料なし */}</p>
        ) : (
          <span />
        )}
        <CsvButton
          filename={`${t('filename')}_${condition.from}_${condition.to}.csv`} /* CSV のファイル名 */
          rows={[
            [t('dimension'), t('segment'), t('amount'), t('orders')], // 見出しの行（分け方、区分、売上額、注文件数）
            ...dimensions.flatMap((dimension) =>
              dimension.rows.map((row) => [dimension.name, row.segment, row.amount, row.orders]),
            ),
          ]}
        />
      </div>
      {dimensions.map((dimension) => (
        <SegmentSection
          horizontal={dimension.key === 'prefecture'}
          key={dimension.key}
          name={dimension.name}
          rows={dimension.rows}
        />
      ))}
    </div>
  )
}

/** 横の棒の 1 本の高さ（px） */
const HORIZONTAL_BAR_HEIGHT = 28

/** 1 つの分け方の棒グラフと表。区分の多いもの（都道府県）は、名前が重ならないよう横の棒にする */
function SegmentSection({ name, rows, horizontal }: { name: string; rows: SegmentRow[]; horizontal: boolean }) {
  const t = useTranslations('report.customerBreakdown')
  const chartConfig = { amount: { label: t('amount'), color: 'var(--chart-3)' } } satisfies ChartConfig // 売上額
  return (
    <section aria-label={t('chart', { dimension: name }) /* 分け方ごとの売上額 */} className="space-y-4">
      <h2 className="font-heading font-semibold text-lg">{name}</h2>
      {horizontal ? (
        <ChartContainer
          className="aspect-auto w-full"
          config={chartConfig}
          style={{ height: Math.max(rows.length * HORIZONTAL_BAR_HEIGHT + 40, 120) }}
        >
          <BarChart data={rows} layout="vertical" margin={{ left: 24, right: 24 }}>
            <CartesianGrid horizontal={false} />
            <XAxis tickFormatter={formatYenCompact} tickLine={false} type="number" />
            <YAxis dataKey="segment" interval={0} tickLine={false} type="category" width={72} />
            <ChartTooltip content={<ChartValueTooltip formatValue={formatYen} />} />
            <Bar dataKey="amount" isAnimationActive={false} fill="var(--color-amount)" radius={4} />
          </BarChart>
        </ChartContainer>
      ) : (
        <ChartContainer className="h-64 w-full" config={chartConfig}>
          <BarChart data={rows} margin={{ left: 24, right: 24 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="segment" interval={0} tickLine={false} />
            <YAxis tickFormatter={formatYenCompact} tickLine={false} width={72} />
            <ChartTooltip content={<ChartValueTooltip formatValue={formatYen} />} />
            <Bar dataKey="amount" isAnimationActive={false} fill="var(--color-amount)" radius={4} />
          </BarChart>
        </ChartContainer>
      )}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{name}</TableHead>
            <TableHead className="text-right">{t('amount') /* 売上額 */}</TableHead>
            <TableHead className="text-right">{t('orders') /* 注文件数 */}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.segment}>
              <TableCell>{row.segment}</TableCell>
              <TableCell className="text-right tabular-nums">{formatYen(row.amount)}</TableCell>
              <TableCell className="text-right tabular-nums">{row.orders}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}
