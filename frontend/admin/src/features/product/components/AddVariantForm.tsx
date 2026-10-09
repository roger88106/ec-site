import { Controller } from 'react-hook-form'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { setServerFieldErrors } from '@/api/formErrors'
import { useInvalidate } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { CodeInput } from '@/components/CodeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { useVersionedForm } from '@/hooks/useVersionedForm'

type Values = { variantCode: string; axisValues: { value: string }[] }

const FIELD_NAMES = ['variantCode', 'axisValues'] as const

function emptyValues(product: ProductDetail): Values {
  return { variantCode: '', axisValues: product.axes.map(() => ({ value: '' })) }
}

/**
 * バリアントの登録（AC-20〜24）。品番の英小文字は大文字にする。<br />
 * 送るバージョンは hooks/useVersionedForm で扱う（管理画面の構成 第 5 節）。
 */
export function AddVariantForm({ product, refetch }: { product: ProductDetail; refetch: () => Promise<unknown> }) {
  const invalidate = useInvalidate()
  const { form, baseVersion, reload } = useVersionedForm(product, emptyValues, refetch)
  const mutation = $api.useMutation('post', '/products/{productId}/variants', {
    onSuccess: async () => {
      toast.success('バリアントを追加しました')
      // 入力を空に戻す。取得し直した商品で、送るバージョンも新しくなる
      form.reset(emptyValues(product))
      await invalidate('/products', '/inventory')
    },
    onError: (error) => setServerFieldErrors(error, form.setError, FIELD_NAMES),
  })
  const values = form.watch()
  const filled = values.variantCode !== '' && values.axisValues.every((axis) => axis.value.trim() !== '')

  return (
    <form
      className="space-y-3 border-t pt-4"
      onSubmit={form.handleSubmit((submitted) =>
        mutation.mutate({
          params: { path: { productId: product.id } },
          body: {
            variantCode: submitted.variantCode,
            axisValues: submitted.axisValues.map((axis) => axis.value.trim()),
            version: baseVersion,
          },
        }),
      )}
    >
      <div className="flex flex-wrap items-start gap-3">
        <Field className="w-56">
          <FieldLabel htmlFor="variant-code">バリアント品番</FieldLabel>
          <Controller
            control={form.control}
            name="variantCode"
            render={({ field }) => (
              <CodeInput id="variant-code" onChange={field.onChange} placeholder="ABC-001-WH-M" value={field.value} />
            )}
          />
          <FieldError errors={[form.formState.errors.variantCode]} />
        </Field>
        {product.axes.map((axis, index) => (
          <Field className="w-32" key={axis}>
            <FieldLabel htmlFor={`variant-axis-${index}`}>{axis}</FieldLabel>
            <Input id={`variant-axis-${index}`} maxLength={50} {...form.register(`axisValues.${index}.value`)} />
          </Field>
        ))}
        <Button className="mt-6" disabled={mutation.isPending || !filled} type="submit">
          バリアントを追加
        </Button>
      </div>
      <ErrorMessage
        error={mutation.error}
        onReload={async () => {
          mutation.reset()
          await reload()
        }}
        shownFields={['variantCode']}
      />
    </form>
  )
}
