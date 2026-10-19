import { formatYen } from '@ec-site/shared/lib/format'
import { keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import type { SalesTrendReport } from '@/api/types'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { type ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CsvButton } from '@/features/report/components/CsvButton'
import { ReportConditionForm } from '@/features/report/components/ReportConditionForm'
import { FISCAL_PRESETS, presetPeriod, todayInJapan } from '@/features/report/period'
import { bucketLabel, formatYenCompact } from '@/features/report/report'
import { parseCondition, type ReportCondition, toSearchParams } from '@/features/report/reportCondition'
import { useReportSettings } from '@/features/report/settings'

/** 売上の推移。期間・区切り・数え方などの条件は URL に持つ */
export function SalesTrendPage() {
  const t = useTranslations('app.menu')
  const settings = useReportSettings()
  return (
    <>
      <PageHeader title={t('salesTrend') /* 売上の推移 */} />
      <QueryState query={settings}>
        {({ fiscalYearStartMonth }) => <SalesTrend fiscalYearStartMonth={fiscalYearStartMonth} />}
      </QueryState>
    </>
  )
}

/** 条件の入力と、グラフと表 */
function SalesTrend({ fiscalYearStartMonth }: { fiscalYearStartMonth: number }) {
  const [searchParams, setSearchParams] = useSearchParams()
  // URL に期間がなければ今年度
  const condition = parseCondition(searchParams, presetPeriod('thisFiscalYear', todayInJapan(), fiscalYearStartMonth))
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
  /** 条件を URL に反映する（URL が条件の正本） */
  const apply = (next: ReportCondition) => setSearchParams(toSearchParams(next))
  return (
    <div className="space-y-6">
      <ReportConditionForm
        condition={condition}
        fiscalYearStartMonth={fiscalYearStartMonth}
        granularity
        key={searchParams.toString()}
        onApply={apply}
        presets={FISCAL_PRESETS}
      />
      <QueryState query={query}>{(report) => <SalesTrendResult condition={condition} report={report} />}</QueryState>
    </div>
  )
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
            <ChartTooltip content={<ChartTooltipContent />} />
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
