import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { orderStatusValues } from '@/api/generated/admin'
import { parameters } from '@/api/generated/constraints'
import type { OrderStatus } from '@/api/types'
import { OptionSelect } from '@/components/OptionSelect'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldLabel } from '@/components/ui/field'
import { orderStatusLabel } from '@/features/order/labels'
import { EMPTY_FILTER, type OrderFilter } from '@/features/order/orderFilter'
import { translate } from '@/i18n/translate'

/** 状態の選択肢（「すべて」は絞り込まない） */
const statusOptions = [
  { value: null, label: translate('common.all') }, // すべて
  ...orderStatusValues.map((status) => ({ value: status, label: orderStatusLabel(status) })),
]

/** 絞り込みの入力。「絞り込む」で URL に反映し、1 ページ目に戻す */
export function OrderFilterForm({ filter, onApply }: { filter: OrderFilter; onApply: (filter: OrderFilter) => void }) {
  const t = useTranslations('order.filter')
  const tApp = useTranslations('app.common')
  const form = useForm<OrderFilter>({ defaultValues: filter })
  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={form.handleSubmit((values) => onApply({ ...values, orderNumber: values.orderNumber.trim(), page: 1 }))}
    >
      <Field className="w-44">
        <FieldLabel htmlFor="filter-order-number">{t('orderNumber') /* 注文番号 */}</FieldLabel>
        <Input
          id="filter-order-number"
          maxLength={parameters.listOrders.orderNumber.maxLength}
          {...form.register('orderNumber')}
        />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="filter-status">{t('status') /* 状態 */}</FieldLabel>
        <Controller
          control={form.control}
          name="status"
          render={({ field }) => (
            <OptionSelect<OrderStatus>
              className="w-40"
              id="filter-status"
              onChange={field.onChange}
              options={statusOptions}
              value={field.value}
            />
          )}
        />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="filter-ordered-from">{t('orderedFrom') /* 注文日から */}</FieldLabel>
        <Input id="filter-ordered-from" type="date" {...form.register('orderedFrom')} />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="filter-ordered-to">{t('orderedTo') /* 注文日まで */}</FieldLabel>
        <Input id="filter-ordered-to" type="date" {...form.register('orderedTo')} />
      </Field>
      <div className="flex h-9 items-center gap-4">
        {(['authorizationExpiring', 'returned'] as const).map((name) => (
          <Controller
            control={form.control}
            key={name}
            name={name}
            render={({ field }) => (
              // biome-ignore lint/a11y/noLabelWithoutControl: Checkbox（base-ui）はラベルの中の button 要素
              <label className="flex items-center gap-2 text-sm">
                <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                {t(name) /* 与信期限が近い、返送 */}
              </label>
            )}
          />
        ))}
      </div>
      <Button type="submit">{tApp('filter.submit') /* 絞り込む */}</Button>
      <Button onClick={() => onApply(EMPTY_FILTER)} type="button" variant="ghost">
        {tApp('filter.clear') /* クリア */}
      </Button>
    </form>
  )
}
