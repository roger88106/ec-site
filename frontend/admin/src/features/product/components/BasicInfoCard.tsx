import { FormProvider } from 'react-hook-form'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { setServerFieldErrors } from '@/api/formErrors'
import { useInvalidate } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ProductFields } from '@/features/product/components/ProductFields'
import { isProductFieldsFilled, PRODUCT_FIELD_NAMES, toProductBody, toProductFields } from '@/features/product/product'
import { useVersionedForm } from '@/hooks/useVersionedForm'

/**
 * 基本情報の編集（AC-18、19、25、26）。商品品番とバリアント軸は表示だけ。<br />
 * 送るバージョンは hooks/useVersionedForm で扱う（管理画面の構成 第 5 節）。
 */
export function BasicInfoCard({ product, refetch }: { product: ProductDetail; refetch: () => Promise<unknown> }) {
  const invalidate = useInvalidate()
  const { form, baseVersion, markSaved, reload } = useVersionedForm(product, toProductFields, refetch)
  const mutation = $api.useMutation('put', '/products/{productId}', {
    onSuccess: async () => {
      toast.success('基本情報を保存しました')
      markSaved()
      await invalidate('/products')
    },
    onError: (error) => setServerFieldErrors(error, form.setError, PRODUCT_FIELD_NAMES),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>基本情報</CardTitle>
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
              <dt className="text-muted-foreground">商品品番</dt>
              <dd className="font-mono">{product.productCode}</dd>
              <dt className="text-muted-foreground">バリアント軸</dt>
              <dd>{product.axes.length > 0 ? product.axes.join('、') : 'なし'}</dd>
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
                保存
              </Button>
            </div>
          </form>
        </FormProvider>
      </CardContent>
    </Card>
  )
}
