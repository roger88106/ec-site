import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useForm } from 'react-hook-form'
import { setServerFieldErrors } from '@/api/formErrors'
import { manualStockAdjustmentReasonValues } from '@/api/generated/admin'
import { schemas } from '@/api/generated/constraints'
import type { ManualStockAdjustmentReason } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { ErrorMessage } from '@/components/ErrorMessage'
import { OptionSelect } from '@/components/OptionSelect'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'
import { formatDelta } from '@/lib/delta'
import { stockAdjustmentReasonLabels } from '@/lib/labels'

/** 入力欄の値（増減量は入力中の文字列のまま持ち、送るときに数にする） */
type Values = { delta: string; reason: ManualStockAdjustmentReason | null; memo: string }

/** 空の入力（調整したら、ここに戻す） */
const EMPTY: Values = { delta: '', reason: null, memo: '' }

/** サーバーの入力検証のエラーを、項目の下に出す欄 */
const FIELD_NAMES = ['delta', 'reason', 'memo'] as const

/** 理由の選択肢（admin.yaml の値と、画面の表示名）。注文に由来する理由は選べない（AC-107） */
const reasonOptions = manualStockAdjustmentReasonValues.map((reason) => ({
  value: reason,
  label: stockAdjustmentReasonLabels[reason],
}))

/**
 * 在庫の調整（AC-42、43、46）。増減量と理由で調整し、在庫数を直接上書きする欄は作らない。<br />
 * 負になる調整や、「その他」でメモがない調整の拒否は、サーバーのエラーを表示する。
 */
export function AdjustStockForm({ variantId }: { variantId: string }) {
  const form = useForm<Values>({ defaultValues: EMPTY })
  const mutation = useApiMutation('post', '/inventory/variants/{variantId}/adjustments', {
    success: (adjustment) =>
      `${formatDelta(adjustment.delta)} を反映し、在庫数は ${adjustment.quantityAfter} になりました`,
    invalidate: ['/inventory'],
    onSuccess: () => form.reset(EMPTY),
    onError: (error) => setServerFieldErrors(error, form.setError, FIELD_NAMES),
  })
  const values = form.watch()
  const filled = values.delta.trim() !== '' && values.reason !== null

  return (
    <Card>
      <CardHeader>
        <CardTitle>調整</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="grid gap-4"
          onSubmit={form.handleSubmit((submitted) =>
            mutation.mutate({
              params: { path: { variantId } },
              body: {
                delta: Number(submitted.delta),
                reason: submitted.reason as ManualStockAdjustmentReason,
                memo: submitted.memo.trim() || null,
              },
            }),
          )}
        >
          <div className="flex flex-wrap items-start gap-3">
            <Field className="w-32">
              <FieldLabel htmlFor="stock-delta">増減量</FieldLabel>
              <Input id="stock-delta" placeholder="+10 / -3" step={1} type="number" {...form.register('delta')} />
              <FieldError errors={[form.formState.errors.delta]} />
            </Field>
            <Field className="w-40">
              <FieldLabel htmlFor="stock-reason">理由</FieldLabel>
              <Controller
                control={form.control}
                name="reason"
                render={({ field }) => (
                  <OptionSelect
                    className="w-40"
                    id="stock-reason"
                    onChange={field.onChange}
                    options={reasonOptions}
                    value={field.value}
                  />
                )}
              />
              <FieldError errors={[form.formState.errors.reason]} />
            </Field>
          </div>
          <Field>
            <FieldLabel htmlFor="stock-memo">メモ{values.reason === 'OTHER' ? '（必須）' : '（任意）'}</FieldLabel>
            <Textarea
              id="stock-memo"
              maxLength={schemas.AdjustStockRequest.memo.maxLength}
              rows={2}
              {...form.register('memo')}
            />
            <FieldError errors={[form.formState.errors.memo]} />
          </Field>
          <ErrorMessage error={mutation.error} shownFields={FIELD_NAMES} />
          <div>
            <Button disabled={mutation.isPending || !filled} type="submit">
              調整する
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
