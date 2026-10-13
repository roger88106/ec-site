import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, FormProvider, useFieldArray, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { setServerFieldErrors } from '@/api/formErrors'
import { schemas } from '@/api/generated/constraints'
import { useApiMutation } from '@/api/useApiMutation'
import { paths } from '@/app/paths'
import { CodeInput } from '@/components/CodeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { PageHeader } from '@/components/PageHeader'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { ProductFields } from '@/features/product/components/ProductFields'
import {
  EMPTY_PRODUCT_FIELDS,
  isProductFieldsFilled,
  PRODUCT_FIELD_NAMES,
  type ProductFieldsValues,
  toProductBody,
} from '@/features/product/product'

/** バリアント軸の上限（AC-13） */
const MAX_AXES = schemas.CreateProductRequest.axes.maxItems

/** 入力欄の値（基本情報に、登録のときだけ入れる品番と軸を足したもの） */
type Values = ProductFieldsValues & {
  productCode: string
  axes: { name: string }[]
}

/** サーバーの入力検証のエラーを、項目の下に出す欄 */
const FIELD_NAMES = [...PRODUCT_FIELD_NAMES, 'productCode', 'axes'] as const

/**
 * 商品の登録（T709。AC-13〜17、25）。<br />
 * 商品品番とバリアント軸は登録後に変更できないので、この画面でだけ入力する。登録後は編集の画面に移る。
 */
export function ProductCreatePage() {
  const navigate = useNavigate()
  const form = useForm<Values>({ defaultValues: { ...EMPTY_PRODUCT_FIELDS, productCode: '', axes: [] } })
  const axes = useFieldArray({ control: form.control, name: 'axes' })
  const mutation = useApiMutation('post', '/products', {
    success: '商品を登録しました',
    invalidate: ['/products'],
    onSuccess: (created) => navigate(paths.product(created.id)),
    onError: (error) => setServerFieldErrors(error, form.setError, FIELD_NAMES),
  })
  const values = form.watch()
  const filled =
    values.productCode !== '' && isProductFieldsFilled(values) && values.axes.every((axis) => axis.name.trim() !== '')

  return (
    <>
      <PageHeader title="商品の登録">
        <Button nativeButton={false} render={<Link to={paths.products()} />} variant="outline">
          一覧へ戻る
        </Button>
      </PageHeader>
      <FormProvider {...form}>
        <form
          className="max-w-3xl space-y-6"
          onSubmit={form.handleSubmit((submitted) =>
            mutation.mutate({
              body: {
                ...toProductBody(submitted),
                productCode: submitted.productCode,
                axes: submitted.axes.map((axis) => axis.name.trim()),
              },
            }),
          )}
        >
          <Card>
            <CardHeader>
              <CardTitle>基本情報</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4">
              <Field className="w-64">
                <FieldLabel htmlFor="product-code">商品品番（登録後は変更できません）</FieldLabel>
                <Controller
                  control={form.control}
                  name="productCode"
                  render={({ field }) => (
                    <CodeInput
                      id="product-code"
                      maxLength={schemas.CreateProductRequest.productCode.maxLength}
                      onChange={field.onChange}
                      placeholder="ABC-001"
                      value={field.value}
                    />
                  )}
                />
                <FieldDescription>半角の英大文字・数字とハイフン。英小文字は大文字にします。</FieldDescription>
                <FieldError errors={[form.formState.errors.productCode]} />
              </Field>
              <ProductFields />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>バリアント軸（登録後は変更できません）</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3">
              <p className="text-muted-foreground text-sm">
                色・サイズなど、バリアントを分ける軸を 0〜{MAX_AXES} 個指定します。軸がなければ、バリアントは 1
                つだけです。
              </p>
              {axes.fields.map((axis, index) => (
                <div className="flex items-center gap-2" key={axis.id}>
                  <Input
                    aria-label={`軸 ${index + 1}`}
                    // 「軸を追加」で増えた欄に入力を移す（autoFocus は欄を作ったときだけ効く）
                    autoFocus
                    className="w-56"
                    maxLength={schemas.CreateProductRequest.axes.items.maxLength}
                    placeholder={['色', 'サイズ', ''][index]}
                    {...form.register(`axes.${index}.name`)}
                  />
                  <Button onClick={() => axes.remove(index)} size="sm" type="button" variant="ghost">
                    削除
                  </Button>
                </div>
              ))}
              <FieldError errors={[form.formState.errors.axes]} />
              <div>
                <Button
                  disabled={axes.fields.length >= MAX_AXES}
                  onClick={() => axes.append({ name: '' })}
                  size="sm"
                  type="button"
                  variant="outline"
                >
                  軸を追加
                </Button>
              </div>
            </CardContent>
          </Card>

          <ErrorMessage error={mutation.error} shownFields={FIELD_NAMES} />
          <Button disabled={mutation.isPending || !filled} type="submit">
            登録
          </Button>
        </form>
      </FormProvider>
    </>
  )
}
