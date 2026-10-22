import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { granularityValues, reportBasisValues } from '@/api/generated/admin'
import { parameters } from '@/api/generated/constraints'
import type { Granularity, ReportBasis } from '@/api/types'
import { LabeledCheckbox } from '@/components/LabeledCheckbox'
import { OptionSelect } from '@/components/OptionSelect'
import { Field, FieldLabel } from '@/components/ui/field'
import { granularityLabel, reportBasisLabel } from '@/features/report/labels'
import { type PeriodPreset, presetPeriod, todayInJapan } from '@/features/report/period'
import type { ReportCondition } from '@/features/report/reportCondition'

/** 商品の選択肢に出す商品の数（商品の一覧の API の 1 ページの上限） */
const PRODUCT_LIMIT = parameters.listProducts.size.maximum

/**
 * レポートの条件の入力（管理画面の構成 第 3 節）。「表示する」で URL に反映する。<br />
 * 期間は、選択肢を選ぶと始まりと終わりの日を埋める。日付を直接入れてもよい。
 *
 * @param condition いまの条件（URL から読んだもの）
 * @param onApply 条件を URL に反映する
 * @param presets 期間の選択肢（売上の推移は会計年度、他は暦）
 * @param fiscalYearStartMonth 会計年度の始まりの月（レポートの設定）
 * @param granularity 区切りを選ぶか（売上の推移だけ）
 * @param excludeShipping 送料を除くかを選ぶか（バリアントの売れ行きは送料を含めないので選ばない）
 * @param productRequired 商品を必ず選ぶか（バリアントの売れ行き）
 */
export function ReportConditionForm({
  condition,
  onApply,
  presets,
  fiscalYearStartMonth,
  granularity = false,
  excludeShipping = true,
  productRequired = false,
}: {
  condition: ReportCondition
  onApply: (condition: ReportCondition) => void
  presets: readonly PeriodPreset[]
  fiscalYearStartMonth: number
  granularity?: boolean
  excludeShipping?: boolean
  productRequired?: boolean
}) {
  const t = useTranslations('report.condition')
  // 粒度と売上の数え方の選択肢
  const granularityOptions = granularityValues.map((value) => ({ value, label: granularityLabel(value) }))
  const basisOptions = reportBasisValues.map((value) => ({ value, label: reportBasisLabel(value) }))
  const form = useForm<ReportCondition>({ defaultValues: condition })
  const products = $api.useQuery('get', '/products', { params: { query: { size: PRODUCT_LIMIT } } })
  const productOptions = [
    ...(productRequired ? [] : [{ value: null, label: t('allProducts') /* 全商品 */ }]),
    ...(products.data?.items ?? []).map((product) => ({ value: product.id, label: product.name })),
  ]
  const presetOptions = [
    { value: null, label: t('presets.custom') /* 日付で指定 */ },
    ...presets.map((preset) => ({ value: preset, label: t(`presets.${preset}`) /* 期間の選択肢の名前 */ })),
  ]

  /** 期間の選択肢を選んだら、始まりと終わりの日を埋める */
  const choosePreset = (preset: PeriodPreset | null) => {
    if (preset === null) {
      return
    }
    const period = presetPeriod(preset, todayInJapan(), fiscalYearStartMonth)
    form.setValue('from', period.from)
    form.setValue('to', period.to)
  }

  return (
    <form className="flex flex-wrap items-end gap-3" onSubmit={form.handleSubmit(onApply)}>
      <Field className="w-36">
        <FieldLabel htmlFor="report-preset">{t('preset') /* 期間の選択肢 */}</FieldLabel>
        <OptionSelect<PeriodPreset>
          className="w-36"
          id="report-preset"
          onChange={choosePreset}
          options={presetOptions}
          value={null}
        />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="report-from">{t('from') /* 期間の始まり */}</FieldLabel>
        <Input id="report-from" required type="date" {...form.register('from')} />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="report-to">{t('to') /* 期間の終わり */}</FieldLabel>
        <Input id="report-to" required type="date" {...form.register('to')} />
      </Field>
      {granularity && (
        <Field className="w-28">
          <FieldLabel htmlFor="report-granularity">{t('granularity') /* 区切り */}</FieldLabel>
          <Controller
            control={form.control}
            name="granularity"
            render={({ field }) => (
              <OptionSelect<Granularity>
                className="w-28"
                id="report-granularity"
                onChange={(value) => field.onChange(value ?? 'MONTH')}
                options={granularityOptions}
                value={field.value}
              />
            )}
          />
        </Field>
      )}
      <Field className="w-32">
        <FieldLabel htmlFor="report-basis">{t('basis') /* 売上の数え方 */}</FieldLabel>
        <Controller
          control={form.control}
          name="basis"
          render={({ field }) => (
            <OptionSelect<ReportBasis>
              className="w-32"
              id="report-basis"
              onChange={(value) => field.onChange(value ?? 'ORDER')}
              options={basisOptions}
              value={field.value}
            />
          )}
        />
      </Field>
      <Field className="w-56">
        <FieldLabel htmlFor="report-product">{t('product') /* 商品 */}</FieldLabel>
        <Controller
          control={form.control}
          name="productId"
          render={({ field }) => (
            <OptionSelect<string>
              className="w-56"
              id="report-product"
              onChange={(value) => field.onChange(value ?? undefined)}
              options={productOptions}
              placeholder={t('selectProduct') /* 商品を選ぶ */}
              value={field.value ?? null}
            />
          )}
        />
      </Field>
      <div className="flex h-9 items-center gap-4">
        {(excludeShipping ? (['excludeTax', 'excludeShipping'] as const) : (['excludeTax'] as const)).map((name) => (
          <Controller
            control={form.control}
            key={name}
            name={name}
            render={({ field }) => (
              <LabeledCheckbox checked={field.value} onCheckedChange={field.onChange}>
                {t(name) /* 消費税を除く、送料を除く */}
              </LabeledCheckbox>
            )}
          />
        ))}
      </div>
      <Button type="submit">{t('submit') /* 表示する */}</Button>
    </form>
  )
}
