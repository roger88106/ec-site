import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import { schemas } from '@/api/generated/constraints'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { CodeInput } from '@/components/CodeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { useVersionedForm } from '@/hooks/useVersionedForm'

/** 入力欄の値（軸の値は、商品の軸と同じ並び） */
type Values = { variantCode: string; axisValues: { value: string }[] }

/** サーバーの入力検証のエラーを、項目の下に出す欄 */
const FIELD_NAMES = ['variantCode', 'axisValues'] as const

/** 空の入力。軸の値の欄は、商品の軸の数だけ作る */
function emptyValues(product: ProductDetail): Values {
  return { variantCode: '', axisValues: product.axes.map(() => ({ value: '' })) }
}

/**
 * バリアントの登録。品番の英小文字は大文字にする。<br />
 * 送るバージョンは hooks/useVersionedForm で扱う（管理画面の構成 第 5 節）。
 */
export function AddVariantForm({ product, refetch }: { product: ProductDetail; refetch: () => Promise<unknown> }) {
  const { form, baseVersion, reload } = useVersionedForm(product, emptyValues, refetch)
  const t = useTranslations('product.variants')
  const mutation = useApiMutation('post', '/products/{productId}/variants', {
    success: t('added'), // 追加の知らせ
    invalidate: ['/products', '/inventory'],
    // 入力を空に戻す。取得し直した商品で、送るバージョンも新しくなる
    onSuccess: () => form.reset(emptyValues(product)),
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
          <FieldLabel htmlFor="variant-code">{t('variantCode') /* バリアント品番 */}</FieldLabel>
          <Controller
            control={form.control}
            name="variantCode"
            render={({ field }) => (
              <CodeInput
                id="variant-code"
                maxLength={schemas.AddVariantRequest.variantCode.maxLength}
                onChange={field.onChange}
                placeholder="ABC-001-WH-M"
                value={field.value}
              />
            )}
          />
          <FieldError errors={[form.formState.errors.variantCode]} />
        </Field>
        {product.axes.map((axis, index) => (
          <Field className="w-32" key={axis}>
            <FieldLabel htmlFor={`variant-axis-${index}`}>{axis}</FieldLabel>
            <Input
              id={`variant-axis-${index}`}
              maxLength={schemas.AddVariantRequest.axisValues.items.maxLength}
              {...form.register(`axisValues.${index}.value`)}
            />
          </Field>
        ))}
        <Button className="mt-6" disabled={mutation.isPending || !filled} type="submit">
          {t('add') /* バリアントを追加 */}
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
