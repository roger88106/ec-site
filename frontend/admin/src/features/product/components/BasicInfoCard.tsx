import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { FormProvider } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { ErrorMessage } from '@/components/ErrorMessage'
import { ProductFields } from '@/features/product/components/ProductFields'
import { isProductFieldsFilled, PRODUCT_FIELD_NAMES, toProductBody, toProductFields } from '@/features/product/product'
import { useVersionedForm } from '@/hooks/useVersionedForm'

/**
 * 基本情報の編集。商品品番とバリアント軸は表示だけ。<br />
 * 送るバージョンは hooks/useVersionedForm で扱う（管理画面の構成 第 5 節）。
 */
export function BasicInfoCard({ product, refetch }: { product: ProductDetail; refetch: () => Promise<unknown> }) {
  const { form, baseVersion, markSaved, reload } = useVersionedForm(product, toProductFields, refetch)
  const t = useTranslations('product.basicInfo')
  const tApp = useTranslations('app.common')
  const mutation = useApiMutation('put', '/products/{productId}', {
    success: t('saved'), // 保存の知らせ
    invalidate: ['/products'],
    onSuccess: markSaved,
    onError: (error) => setServerFieldErrors(error, form.setError, PRODUCT_FIELD_NAMES),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 基本情報 */}</CardTitle>
      </CardHeader>
      <CardContent>
        <FormProvider {...form}>
          <form
            className="grid gap-4"
            onSubmit={form.handleSubmit((values) =>
              mutation.mutate({
                params: { path: { productId: product.id } },
                body: { ...toProductBody(values), version: baseVersion },
              }),
            )}
          >
            <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-muted-foreground">{t('productCode') /* 商品品番 */}</dt>
              <dd className="font-mono">{product.productCode}</dd>
              <dt className="text-muted-foreground">{t('axes') /* バリアント軸 */}</dt>
              <dd>
                {
                  product.axes.length > 0
                    ? product.axes.join(t('axisSeparator'))
                    : tApp('none') /* 軸の並び、なければ「なし」 */
                }
              </dd>
            </dl>
            <ProductFields />
            <ErrorMessage
              error={mutation.error}
              onReload={async () => {
                mutation.reset()
                await reload()
              }}
              shownFields={PRODUCT_FIELD_NAMES}
            />
            <div>
              <Button disabled={mutation.isPending || !isProductFieldsFilled(form.watch())} type="submit">
                {tApp('save') /* 保存 */}
              </Button>
            </div>
          </form>
        </FormProvider>
      </CardContent>
    </Card>
  )
}
