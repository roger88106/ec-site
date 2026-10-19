import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { publicationStatusValues } from '@/api/generated/admin'
import { parameters } from '@/api/generated/constraints'
import type { PublicationStatus } from '@/api/types'
import { CategorySelect } from '@/components/CategorySelect'
import { CodeInput } from '@/components/CodeInput'
import { OptionSelect } from '@/components/OptionSelect'
import { Field, FieldLabel } from '@/components/ui/field'
import { EMPTY_FILTER, type ProductFilter } from '@/features/product/productFilter'
import { translate } from '@/i18n/translate'
import { publicationStatusLabel } from '@/lib/labels'

/** 状態の選択肢（「すべて」は絞り込まない） */
const statusOptions = [
  { value: null, label: translate('common.all') }, // すべて
  ...publicationStatusValues.map((status) => ({ value: status, label: publicationStatusLabel(status) })),
]

/** 絞り込みの入力。「絞り込む」で URL に反映し、1 ページ目に戻す */
export function ProductFilterForm({
  filter,
  onApply,
}: {
  filter: ProductFilter
  onApply: (filter: ProductFilter) => void
}) {
  const t = useTranslations('product.filter')
  const tApp = useTranslations('app.common')
  const form = useForm<ProductFilter>({ defaultValues: filter })
  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={form.handleSubmit((values) =>
        onApply({ ...values, code: values.code.trim(), name: values.name.trim(), page: 1 }),
      )}
    >
      <Field className="w-40">
        <FieldLabel htmlFor="filter-code">{t('code') /* 品番 */}</FieldLabel>
        <Controller
          control={form.control}
          name="code"
          render={({ field }) => (
            <CodeInput
              id="filter-code"
              maxLength={parameters.listProducts.code.maxLength}
              onChange={field.onChange}
              value={field.value}
            />
          )}
        />
      </Field>
      <Field className="w-56">
        <FieldLabel htmlFor="filter-name">{t('name') /* 商品名 */}</FieldLabel>
        <Input id="filter-name" maxLength={parameters.listProducts.name.maxLength} {...form.register('name')} />
      </Field>
      <Field className="w-32">
        <FieldLabel htmlFor="filter-status">{t('status') /* 状態 */}</FieldLabel>
        <Controller
          control={form.control}
          name="status"
          render={({ field }) => (
            <OptionSelect<PublicationStatus>
              className="w-32"
              id="filter-status"
              onChange={field.onChange}
              options={statusOptions}
              value={field.value}
            />
          )}
        />
      </Field>
      <Field className="w-48">
        <FieldLabel htmlFor="filter-category">{t('category') /* カテゴリ */}</FieldLabel>
        <Controller
          control={form.control}
          name="categoryId"
          render={({ field }) => <CategorySelect id="filter-category" onChange={field.onChange} value={field.value} />}
        />
      </Field>
      <Button type="submit">{tApp('filter.submit') /* 絞り込む */}</Button>
      <Button onClick={() => onApply(EMPTY_FILTER)} type="button" variant="ghost">
        {tApp('filter.clear') /* クリア */}
      </Button>
    </form>
  )
}
