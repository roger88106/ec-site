import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, useFieldArray } from 'react-hook-form'
import type { TaxCategory } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { DateTimeInput } from '@/components/DateTimeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { rateState } from '@/features/tax/rates'
import { useVersionedForm } from '@/hooks/useVersionedForm'

/** 編集中の税率の 1 行。rateId がなければ新しい税率 */
type RateRow = {
  rateId: string | null
  ratePercent: string
  effectiveFrom: string | null
  effectiveTo: string | null
}

/** 入力欄の値（税率の一覧） */
type Values = { rates: RateRow[] }

/** 税区分の税率の一覧を、入力欄の行にする（率は入力中の文字列として持つ） */
function toValues(taxCategory: TaxCategory): Values {
  return {
    rates: taxCategory.rates.map((rate) => ({
      rateId: rate.id,
      ratePercent: String(rate.ratePercent),
      effectiveFrom: rate.effectiveFrom ?? null,
      effectiveTo: rate.effectiveTo ?? null,
    })),
  }
}

/**
 * 税率の一覧の編集（AC-2〜5、7）。一覧ごと置き換えて保存する。<br />
 * 期間の重複・途切れ、過去の根拠の変更（AC-3、5）はサーバーが検証し、そのエラーを表示する。
 */
export function TaxRatesEditor({
  taxCategory,
  refetch,
}: {
  taxCategory: TaxCategory
  refetch: () => Promise<unknown>
}) {
  const { form, baseVersion, markSaved, reload } = useVersionedForm(taxCategory, toValues, refetch)
  const rates = useFieldArray({ control: form.control, name: 'rates' })
  const mutation = useApiMutation('put', '/tax-categories/{taxCategoryId}/rates', {
    success: '税率を保存しました',
    invalidate: ['/tax-categories'],
    onSuccess: markSaved,
  })
  const rows = form.watch('rates')
  const incomplete = rows.length === 0 || rows.some((row) => row.ratePercent.trim() === '')

  const save = form.handleSubmit((values) =>
    mutation.mutate({
      params: { path: { taxCategoryId: taxCategory.id } },
      body: {
        version: baseVersion,
        rates: values.rates.map((row) => ({
          id: row.rateId,
          ratePercent: Number(row.ratePercent),
          effectiveFrom: row.effectiveFrom,
          effectiveTo: row.effectiveTo,
        })),
      },
    }),
  )

  return (
    <form className="space-y-4" onSubmit={save}>
      <p className="text-muted-foreground text-sm">
        日時は日本時間。開始が空白なら過去すべて、終了が空白なら無期限。終了日時ちょうどは含まない。
        期間は重複も途切れもなく並べ、最後の税率の終了は空白にする。
      </p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-32">率（%）</TableHead>
            <TableHead>適用開始</TableHead>
            <TableHead>適用終了</TableHead>
            <TableHead>状態</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {rates.fields.map((field, index) => (
            <TableRow key={field.id}>
              <TableCell>
                <Input
                  aria-label="率（%）"
                  max={99.99}
                  min={0}
                  step={0.01}
                  type="number"
                  {...form.register(`rates.${index}.ratePercent`)}
                />
              </TableCell>
              <TableCell>
                <Controller
                  control={form.control}
                  name={`rates.${index}.effectiveFrom`}
                  render={({ field: input }) => (
                    <DateTimeInput aria-label="適用開始" onChange={input.onChange} value={input.value} />
                  )}
                />
              </TableCell>
              <TableCell>
                <Controller
                  control={form.control}
                  name={`rates.${index}.effectiveTo`}
                  render={({ field: input }) => (
                    <DateTimeInput aria-label="適用終了" onChange={input.onChange} value={input.value} />
                  )}
                />
              </TableCell>
              <TableCell className="text-muted-foreground text-sm">{rateState(rows[index])}</TableCell>
              <TableCell className="text-right">
                <Button onClick={() => rates.remove(index)} size="sm" type="button" variant="ghost">
                  削除
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <div className="flex gap-2">
        <Button
          // 新しい行の開始は、直前の行の終了から始める（途切れを作らないため）
          onClick={() =>
            rates.append({
              rateId: null,
              ratePercent: '',
              effectiveFrom: rows.at(-1)?.effectiveTo ?? null,
              effectiveTo: null,
            })
          }
          type="button"
          variant="outline"
        >
          税率を追加
        </Button>
        <Button disabled={mutation.isPending || incomplete} type="submit">
          保存
        </Button>
      </div>
      <ErrorMessage
        error={mutation.error}
        onReload={async () => {
          mutation.reset()
          await reload()
        }}
      />
    </form>
  )
}
