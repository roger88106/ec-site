import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useForm } from 'react-hook-form'
import { orderStatusValues } from '@/api/generated/admin'
import type { OrderStatus } from '@/api/types'
import { OptionSelect } from '@/components/OptionSelect'
import { Field, FieldLabel } from '@/components/ui/field'
import { EMPTY_FILTER, type OrderFilter } from '@/features/order/orderFilter'
import { orderStatusLabels } from '@/lib/labels'

/** 状態の選択肢（「すべて」は絞り込まない） */
const statusOptions = [
  { value: null, label: 'すべて' },
  ...orderStatusValues.map((status) => ({ value: status, label: orderStatusLabels[status] })),
]

/** 絞り込みの入力（AC-98）。「絞り込む」で URL に反映し、1 ページ目に戻す */
export function OrderFilterForm({ filter, onApply }: { filter: OrderFilter; onApply: (filter: OrderFilter) => void }) {
  const form = useForm<OrderFilter>({ defaultValues: filter })
  return (
    <form
      className="flex flex-wrap items-end gap-3"
      onSubmit={form.handleSubmit((values) => onApply({ ...values, orderNumber: values.orderNumber.trim(), page: 1 }))}
    >
      <Field className="w-44">
        <FieldLabel htmlFor="filter-order-number">注文番号（前方一致）</FieldLabel>
        <Input id="filter-order-number" maxLength={15} {...form.register('orderNumber')} />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="filter-status">状態</FieldLabel>
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
        <FieldLabel htmlFor="filter-ordered-from">注文日（から）</FieldLabel>
        <Input id="filter-ordered-from" type="date" {...form.register('orderedFrom')} />
      </Field>
      <Field className="w-40">
        <FieldLabel htmlFor="filter-ordered-to">注文日（まで）</FieldLabel>
        <Input id="filter-ordered-to" type="date" {...form.register('orderedTo')} />
      </Field>
      <Button type="submit">絞り込む</Button>
      <Button onClick={() => onApply(EMPTY_FILTER)} type="button" variant="ghost">
        クリア
      </Button>
    </form>
  )
}
