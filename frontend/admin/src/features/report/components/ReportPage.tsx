import { PageHeader } from '@ec-site/shared/components/PageHeader'
import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { QueryState } from '@/components/QueryState'
import { ReportConditionForm, type ReportConditionItems } from '@/features/report/components/ReportConditionForm'
import { type PeriodPreset, presetPeriod, todayInJapan } from '@/features/report/period'
import { parseCondition, type ReportCondition, toSearchParams } from '@/features/report/reportCondition'
import { useReportSettings } from '@/features/report/settings'

/**
 * レポートの画面の共通の骨格
 *
 * 見出し、レポートの設定の取得、条件の入力と URL の読み書きを受け持つ。
 * 条件は URL に持つ（URL が正本）。取得と表示は、条件を受け取る関数 `children` で渡す。
 */
export function ReportPage({
  menuKey,
  defaultPreset,
  presets,
  granularity,
  excludeShipping,
  productRequired,
  children,
}: ReportConditionItems & {
  /** 見出しにするメニューの名前のキー */
  menuKey: 'salesTrend' | 'variantSales' | 'customerBreakdown'
  /** URL に期間がないときの期間 */
  defaultPreset: PeriodPreset
  /** 期間の選択肢（売上の推移は会計年度、他は暦） */
  presets: readonly PeriodPreset[]
  /** 条件から、取得と表示を作る関数 */
  children: (condition: ReportCondition) => ReactNode
}) {
  const t = useTranslations('app.menu')
  const settings = useReportSettings()
  return (
    <>
      <PageHeader title={t(menuKey) /* レポートの名前 */} />
      <QueryState query={settings}>
        {({ fiscalYearStartMonth }) => (
          <ReportBody
            defaultPreset={defaultPreset}
            excludeShipping={excludeShipping}
            fiscalYearStartMonth={fiscalYearStartMonth}
            granularity={granularity}
            presets={presets}
            productRequired={productRequired}
          >
            {children}
          </ReportBody>
        )}
      </QueryState>
    </>
  )
}

/** 条件の入力と、条件から作る取得と表示 */
function ReportBody({
  defaultPreset,
  presets,
  fiscalYearStartMonth,
  granularity,
  excludeShipping,
  productRequired,
  children,
}: ReportConditionItems & {
  defaultPreset: PeriodPreset
  presets: readonly PeriodPreset[]
  fiscalYearStartMonth: number
  children: (condition: ReportCondition) => ReactNode
}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const condition = parseCondition(searchParams, presetPeriod(defaultPreset, todayInJapan(), fiscalYearStartMonth))
  /** 条件を URL に反映する（URL が条件の正本）。 */
  const apply = (next: ReportCondition) => setSearchParams(toSearchParams(next))
  return (
    <div className="space-y-6">
      <ReportConditionForm
        condition={condition}
        excludeShipping={excludeShipping}
        fiscalYearStartMonth={fiscalYearStartMonth}
        granularity={granularity}
        key={searchParams.toString()}
        onApply={apply}
        presets={presets}
        productRequired={productRequired}
      />
      {children(condition)}
    </div>
  )
}
