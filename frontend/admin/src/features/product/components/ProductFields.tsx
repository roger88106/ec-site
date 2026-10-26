import { flattenTree } from '@ec-site/shared/lib/categoryTree'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useFormContext } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { LabeledCheckbox } from '@/components/LabeledCheckbox'
import { OptionSelect } from '@/components/OptionSelect'
import { Field, FieldError, FieldLabel, FieldSet } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import type { ProductFieldsValues } from '@/features/product/product'
import { formatRate } from '@/lib/rate'

/** 商品の項目の入力の制約（登録と編集で、それぞれのリクエストの制約から引いて渡す） */
type ProductFieldConstraints = {
  nameMaxLength: number
  descriptionMaxLength: number
  priceMinimum: number
}

/**
 * 操作の契約のスキーマ（登録・更新の要求）から、商品の項目の入力の制約を作る。
 *
 * @param schema 商品名・説明・税抜価格の制約を持つ要求のスキーマ
 * @returns 商品名・説明の最大の長さと、税抜価格の最小の値
 */
export function productFieldConstraints(schema: {
  name: { maxLength: number }
  description: { maxLength: number }
  priceExclTax: { minimum: number }
}): ProductFieldConstraints {
  return {
    nameMaxLength: schema.name.maxLength,
    descriptionMaxLength: schema.description.maxLength,
    priceMinimum: schema.priceExclTax.minimum,
  }
}

/**
 * 商品名、説明、税区分、税抜価格、所属カテゴリの入力欄（登録と編集で共通）。<br />
 * 外側の FormProvider のフォームに、これらの項目を持たせる。
 */
export function ProductFields({ constraints }: { constraints: ProductFieldConstraints }) {
  const t = useTranslations('product.fields')
  const { register, control, formState } = useFormContext<ProductFieldsValues>()
  const errors = formState.errors
  return (
    <div className="grid gap-4">
      <Field>
        <FieldLabel htmlFor="product-name">{t('name') /* 商品名 */}</FieldLabel>
        <Input id="product-name" maxLength={constraints.nameMaxLength} {...register('name', { required: true })} />
        <FieldError errors={[errors.name]} />
      </Field>
      <Field>
        <FieldLabel htmlFor="product-description">{t('description') /* 説明 */}</FieldLabel>
        <Textarea
          id="product-description"
          maxLength={constraints.descriptionMaxLength}
          rows={4}
          {...register('description')}
        />
        <FieldError errors={[errors.description]} />
      </Field>
      <div className="flex flex-wrap gap-4">
        <Field className="w-64">
          <FieldLabel htmlFor="product-tax-category">{t('taxCategory') /* 税区分 */}</FieldLabel>
          <Controller
            control={control}
            name="taxCategoryId"
            render={({ field }) => (
              <TaxCategorySelect id="product-tax-category" onChange={field.onChange} value={field.value} />
            )}
          />
          <FieldError errors={[errors.taxCategoryId]} />
        </Field>
        <Field className="w-40">
          <FieldLabel htmlFor="product-price">{t('priceExclTax') /* 税抜価格 */}</FieldLabel>
          <Input
            id="product-price"
            min={constraints.priceMinimum}
            step={1}
            type="number"
            {...register('priceExclTax', { required: true })}
          />
          <FieldError errors={[errors.priceExclTax]} />
        </Field>
      </div>
      <FieldSet>
        <span className="font-medium text-sm">{t('categories') /* 所属カテゴリ */}</span>
        <Controller
          control={control}
          name="categoryIds"
          render={({ field }) => <CategoryCheckboxes onChange={field.onChange} value={field.value} />}
        />
        <FieldError errors={[errors.categoryIds]} />
      </FieldSet>
    </div>
  )
}

/** 税区分の選択。現時点の率を併記する（率がない税区分は割り当てられない） */
function TaxCategorySelect({
  id,
  value,
  onChange,
}: {
  id: string
  value: string | null
  onChange: (value: string | null) => void
}) {
  const query = $api.useQuery('get', '/tax-categories')
  const t = useTranslations('product.fields')
  /** 税区分の選択肢。選ぶときの目安に、現時点の率を名前に添える */
  const options = (query.data?.items ?? []).map((taxCategory) => ({
    value: taxCategory.id,
    label: t('taxCategoryOption', { name: taxCategory.name, rate: formatRate(taxCategory.currentRatePercent) }), // 名前と現時点の率
  }))
  return <OptionSelect className="w-64" id={id} onChange={onChange} options={options} value={value} />
}

/** 所属カテゴリの選択。木を字下げして並べる */
function CategoryCheckboxes({ value, onChange }: { value: string[]; onChange: (value: string[]) => void }) {
  const t = useTranslations('product.fields')
  const query = $api.useQuery('get', '/categories')
  const categories = flattenTree(query.data?.items ?? [])
  if (categories.length === 0) {
    return <p className="text-muted-foreground text-sm">{t('noCategories') /* まだない */}</p>
  }
  return (
    <ul className="grid gap-1 rounded-md border p-3">
      {categories.map((category) => (
        <li key={category.id} style={{ paddingLeft: `${(category.depth - 1) * 1.5}rem` }}>
          <LabeledCheckbox
            checked={value.includes(category.id)}
            onCheckedChange={(checked) =>
              onChange(checked ? [...value, category.id] : value.filter((categoryId) => categoryId !== category.id))
            }
          >
            {category.name}
          </LabeledCheckbox>
        </li>
      ))}
    </ul>
  )
}
