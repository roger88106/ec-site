import { LinkButton } from '@ec-site/shared/components/LinkButton'
import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { Controller, FormProvider, useFieldArray, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import { schemas } from '@/api/generated/constraints'
import { useApiMutation } from '@/api/useApiMutation'
import { paths } from '@/app/paths'
import { CodeInput } from '@/components/CodeInput'
import { ErrorMessage } from '@/components/ErrorMessage'
import { PageHeader } from '@/components/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { ProductFields } from '@/features/product/components/ProductFields'
import {
  EMPTY_PRODUCT_FIELDS,
  isProductFieldsFilled,
  PRODUCT_FIELD_NAMES,
  type ProductFieldsValues,
  toProductBody,
} from '@/features/product/product'

/** 商品の項目の入力の制約（登録のリクエストの制約） */
const PRODUCT_FIELD_CONSTRAINTS = {
  nameMaxLength: schemas.CreateProductRequest.name.maxLength,
  descriptionMaxLength: schemas.CreateProductRequest.description.maxLength,
  priceMinimum: schemas.CreateProductRequest.priceExclTax.minimum,
}

/** バリアント軸の上限 */
const MAX_AXES = schemas.CreateProductRequest.axes.maxItems

/** 入力欄の値（基本情報に、登録のときだけ入れる品番と軸を追加したもの） */
type Values = ProductFieldsValues & {
  productCode: string
  axes: { name: string }[]
}

/** サーバーの入力検証のエラーを、項目の下に出す欄 */
const FIELD_NAMES = [...PRODUCT_FIELD_NAMES, 'productCode', 'axes'] as const

/**
 * 商品の登録。<br />
 * 商品品番とバリアント軸は登録後に変更できないので、この画面でだけ入力する。登録後は編集の画面に移る。
 */
export function ProductCreatePage() {
  const t = useTranslations('product.create')
  const tApp = useTranslations('app.common')
  const navigate = useNavigate()
  const form = useForm<Values>({ defaultValues: { ...EMPTY_PRODUCT_FIELDS, productCode: '', axes: [] } })
  const axes = useFieldArray({ control: form.control, name: 'axes' })
  const mutation = useApiMutation('post', '/products', {
    success: t('registered'), // 登録の知らせ
    invalidate: ['/products'],
    onSuccess: (created) => navigate(paths.product(created.id)),
    onError: (error) => setServerFieldErrors(error, form.setError, FIELD_NAMES),
  })
  const values = form.watch()
  const filled =
    values.productCode !== '' && isProductFieldsFilled(values) && values.axes.every((axis) => axis.name.trim() !== '')

  return (
    <>
      <PageHeader title={t('title') /* 商品の登録 */}>
        <LinkButton link={<Link to={paths.products()} />} variant="outline">
          {tApp('backToList') /* 一覧へ戻る */}
        </LinkButton>
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
              <CardTitle>{t('basicInfo') /* 基本情報 */}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4">
              <Field className="w-64">
                <FieldLabel htmlFor="product-code">{t('productCode') /* 商品品番 */}</FieldLabel>
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
                <FieldDescription>{t('productCodeHelp') /* 品番の形 */}</FieldDescription>
                <FieldError errors={[form.formState.errors.productCode]} />
              </Field>
              <ProductFields constraints={PRODUCT_FIELD_CONSTRAINTS} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('axes') /* バリアント軸 */}</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3">
              <p className="text-muted-foreground text-sm">{t('axesHelp', { max: MAX_AXES }) /* 軸の数の説明 */}</p>
              {axes.fields.map((axis, index) => (
                <div className="flex items-center gap-2" key={axis.id}>
                  <Input
                    aria-label={t('axis', { number: index + 1 }) /* 軸の番号 */}
                    // 「軸を追加」で増えた欄に入力を移す（autoFocus は欄を作ったときだけ効く）
                    autoFocus
                    className="w-56"
                    maxLength={schemas.CreateProductRequest.axes.items.maxLength}
                    placeholder={[t('axisPlaceholder1'), t('axisPlaceholder2'), ''][index] /* 軸の例 */}
                    {...form.register(`axes.${index}.name`)}
                  />
                  <Button onClick={() => axes.remove(index)} size="sm" type="button" variant="ghost">
                    {tApp('delete') /* 削除 */}
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
                  {t('addAxis') /* 軸を追加 */}
                </Button>
              </div>
            </CardContent>
          </Card>

          <ErrorMessage error={mutation.error} shownFields={FIELD_NAMES} />
          <Button disabled={mutation.isPending || !filled} type="submit">
            {tApp('register') /* 登録 */}
          </Button>
        </form>
      </FormProvider>
    </>
  )
}
