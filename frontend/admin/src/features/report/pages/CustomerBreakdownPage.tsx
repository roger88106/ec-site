import { keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import type { CustomerBreakdownReport } from '@/api/types'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { type ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CsvButton } from '@/features/report/components/CsvButton'
import { ReportConditionForm } from '@/features/report/components/ReportConditionForm'
import { CALENDAR_PRESETS, presetPeriod, todayInJapan } from '@/features/report/period'
import { ageGroupLabel, prefectureLabel, reportGenderLabel } from '@/features/report/report'
import { parseCondition, type ReportCondition, toSearchParams } from '@/features/report/reportCondition'
import { useReportSettings } from '@/features/report/settings'
import { formatYen } from '@/lib/format'

/** 地域・年齢層・性別。条件は URL に持つ */
export function CustomerBreakdownPage() {
  const t = useTranslations('app.menu')
  const settings = useReportSettings()
  return (
    <>
      <PageHeader title={t('customerBreakdown') /* 地域・年齢層・性別 */} />
      <QueryState query={settings}>
        {({ fiscalYearStartMonth }) => <CustomerBreakdown fiscalYearStartMonth={fiscalYearStartMonth} />}
      </QueryState>
    </>
  )
}

/** 条件の入力と、3 つの分け方のグラフと表 */
function CustomerBreakdown({ fiscalYearStartMonth }: { fiscalYearStartMonth: number }) {
  const [searchParams, setSearchParams] = useSearchParams()
  // URL に期間がなければ今月
  const condition = parseCondition(searchParams, presetPeriod('thisMonth', todayInJapan(), fiscalYearStartMonth))
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
  /** 条件を URL に反映する（URL が条件の正本） */
  const apply = (next: ReportCondition) => setSearchParams(toSearchParams(next))
  return (
    <div className="space-y-6">
      <ReportConditionForm
        condition={condition}
        fiscalYearStartMonth={fiscalYearStartMonth}
        key={searchParams.toString()}
        onApply={apply}
        presets={CALENDAR_PRESETS}
      />
      <QueryState query={query}>{(report) => <BreakdownResult condition={condition} report={report} />}</QueryState>
    </div>
  )
}

/** 1 つの分け方の行 */
type SegmentRow = { segment: string; amount: number; orders: number }

/** 3 つの分け方のグラフと表。CSV は 3 つの表を 1 つにまとめる */
function BreakdownResult({ condition, report }: { condition: ReportCondition; report: CustomerBreakdownReport }) {
  const t = useTranslations('report.customerBreakdown')
  const dimensions = [
    {
      key: 'prefecture',
      name: t('prefecture'),
      rows: report.prefectures.map((row) => ({ ...row, segment: prefectureLabel(row.prefectureCode) })),
    },
    {
      key: 'ageGroup',
      name: t('ageGroup'),
      rows: report.ageGroups.map((row) => ({ ...row, segment: ageGroupLabel(row.ageGroup) })),
    },
    {
      key: 'gender',
      name: t('gender'),
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
          filename={`${t('filename')}_${condition.from}_${condition.to}.csv`}
          rows={[
            [t('dimension'), t('segment'), t('amount'), t('orders')],
            ...dimensions.flatMap((dimension) =>
              dimension.rows.map((row) => [dimension.name, row.segment, row.amount, row.orders]),
            ),
          ]}
        />
      </div>
      {dimensions.map((dimension) => (
        <SegmentSection key={dimension.key} name={dimension.name} rows={dimension.rows} />
      ))}
    </div>
  )
}

/** 1 つの分け方の棒グラフと表 */
function SegmentSection({ name, rows }: { name: string; rows: SegmentRow[] }) {
  const t = useTranslations('report.customerBreakdown')
  const chartConfig = { amount: { label: t('amount'), color: 'var(--chart-3)' } } satisfies ChartConfig
  return (
    <section aria-label={t('chart', { dimension: name }) /* 分け方ごとの売上額 */} className="space-y-4">
      <h2 className="font-heading font-semibold text-lg">{name}</h2>
      <ChartContainer className="h-64 w-full" config={chartConfig}>
        <BarChart data={rows} margin={{ left: 24, right: 24 }}>
          <CartesianGrid vertical={false} />
          <XAxis dataKey="segment" interval={0} tickLine={false} />
          <YAxis tickFormatter={(value: number) => formatYen(value)} tickLine={false} width={96} />
          <ChartTooltip content={<ChartTooltipContent />} />
          <Bar dataKey="amount" fill="var(--color-amount)" radius={4} />
        </BarChart>
      </ChartContainer>
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
