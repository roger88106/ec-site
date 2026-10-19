import { keepPreviousData } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import type { VariantSales, VariantSalesReport } from '@/api/types'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { type ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CsvButton } from '@/features/report/components/CsvButton'
import { ReportConditionForm } from '@/features/report/components/ReportConditionForm'
import { CALENDAR_PRESETS, presetPeriod, todayInJapan } from '@/features/report/period'
import { parseCondition, type ReportCondition, toSearchParams } from '@/features/report/reportCondition'
import { useReportSettings } from '@/features/report/settings'
import { formatYen } from '@/lib/format'

/** バリエーションの売れ行き。商品を選んでから出す。条件は URL に持つ */
export function VariantSalesPage() {
  const t = useTranslations('app.menu')
  const settings = useReportSettings()
  return (
    <>
      <PageHeader title={t('variantSales') /* バリエーションの売れ行き */} />
      <QueryState query={settings}>
        {({ fiscalYearStartMonth }) => <VariantSalesView fiscalYearStartMonth={fiscalYearStartMonth} />}
      </QueryState>
    </>
  )
}

/** 条件の入力と、商品を選んでいればグラフと表 */
function VariantSalesView({ fiscalYearStartMonth }: { fiscalYearStartMonth: number }) {
  const t = useTranslations('report.variantSales')
  const [searchParams, setSearchParams] = useSearchParams()
  // URL に期間がなければ今月
  const condition = parseCondition(searchParams, presetPeriod('thisMonth', todayInJapan(), fiscalYearStartMonth))
  /** 条件を URL に反映する（URL が条件の正本） */
  const apply = (next: ReportCondition) => setSearchParams(toSearchParams(next))
  return (
    <div className="space-y-6">
      <ReportConditionForm
        condition={condition}
        excludeShipping={false}
        fiscalYearStartMonth={fiscalYearStartMonth}
        key={searchParams.toString()}
        onApply={apply}
        presets={CALENDAR_PRESETS}
        productRequired
      />
      {condition.productId === undefined ? (
        <p className="text-muted-foreground text-sm">{t('selectProduct') /* 商品を選ぶ */}</p>
      ) : (
        <VariantSalesQuery condition={condition} productId={condition.productId} />
      )}
    </div>
  )
}

/** 選んだ商品のバリエーションの売れ行きを取得して出す */
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

/** バリエーションの名前（軸の値を「 / 」でつなぐ。軸がなければ品番） */
function variantName(variant: VariantSales): string {
  return variant.axisValues.length > 0 ? variant.axisValues.map((axis) => axis.value).join(' / ') : variant.variantCode
}

/** バリエーションごとの棒グラフと表（販売数量の多い順） */
function VariantSalesResult({ condition, report }: { condition: ReportCondition; report: VariantSalesReport }) {
  const t = useTranslations('report.variantSales')
  const rows = report.variants.map((variant) => ({ ...variant, name: variantName(variant) }))
  const chartConfig = { quantity: { label: t('quantity'), color: 'var(--chart-2)' } } satisfies ChartConfig // 販売数量
  return (
    <div className="space-y-6">
      <p className="font-medium">{t('product', { name: report.productName }) /* 商品名 */}</p>
      <section aria-label={t('chart') /* バリエーションごとの販売数量 */}>
        <ChartContainer className="h-72 w-full" config={chartConfig}>
          <BarChart data={rows} margin={{ left: 24, right: 24 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="name" tickLine={false} />
            <YAxis allowDecimals={false} tickLine={false} />
            <ChartTooltip content={<ChartTooltipContent />} />
            <Bar dataKey="quantity" isAnimationActive={false} fill="var(--color-quantity)" radius={4} />
          </BarChart>
        </ChartContainer>
      </section>
      <div className="flex justify-end">
        <CsvButton
          filename={`${t('filename')}_${report.productName}_${condition.from}_${condition.to}.csv`} /* CSV のファイル名 */
          rows={[
            [t('variant'), t('variantCode'), t('quantity'), t('amount')], // 見出しの行（バリエーション、品番、販売数量、売上額）
            ...rows.map((row) => [row.name, row.variantCode, row.quantity, row.amount]),
          ]}
        />
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('variant') /* バリエーション */}</TableHead>
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
