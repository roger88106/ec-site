import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Table, TableBody, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AddVariantForm } from '@/features/product/components/AddVariantForm'
import { VariantRow, type VariantRowActions } from '@/features/product/components/VariantRow'
import { move } from '@/lib/array'

/**
 * バリアントの一覧、登録、削除、並び替え、販売停止（T709、T1003。AC-20〜24、27、28、71）。<br />
 * 並び替えと削除は、表示している一覧のバージョンで送る。
 */
export function VariantSection({ product, refetch }: { product: ProductDetail; refetch: () => Promise<unknown> }) {
  const reorder = useApiMutation('put', '/products/{productId}/variants/order', { invalidate: ['/products'] })
  const remove = useApiMutation('delete', '/products/{productId}/variants/{variantId}', {
    success: 'バリアントを削除しました',
    invalidate: ['/products', '/inventory'],
  })
  const suspend = useApiMutation('put', '/products/{productId}/variants/{variantId}/suspend', {
    success: '販売停止にしました',
    invalidate: ['/products'],
  })
  const resume = useApiMutation('put', '/products/{productId}/variants/{variantId}/resume', {
    success: '販売を再開しました',
    invalidate: ['/products'],
  })
  const setImage = useApiMutation('put', '/products/{productId}/variants/{variantId}/image', {
    success: 'バリアントの画像を変更しました',
    invalidate: ['/products'],
  })
  // バリアントの画像は、その商品の画像から選ぶ。指定しなければ代表画像（AC-38）
  const imageOptions = [
    { value: null, label: '代表画像' },
    ...product.images.map((image, index) => ({ value: image.id, label: `画像 ${index + 1}` })),
  ]
  const ids = product.variants.map((variant) => variant.id)
  // 別の操作を始めたら、前の操作のエラーを消す
  const { pending, error, resetErrors } = useMutationGroup(reorder, remove, suspend, resume, setImage)
  /** 表示している商品のバージョンで、バリアントの並びを送る */
  const reorderTo = (variantIds: string[]) => {
    resetErrors()
    reorder.mutate({
      params: { path: { productId: product.id } },
      body: { variantIds, version: product.version },
    })
  }
  /** バリアントの 1 行の操作。どれも前の操作のエラーを消してから送る */
  const rowActions = (variantId: string, index: number): VariantRowActions => {
    const path = { productId: product.id, variantId }
    const run = (action: () => void) => () => {
      resetErrors()
      action()
    }
    return {
      moveUp: () => reorderTo(move(ids, index, index - 1)),
      moveDown: () => reorderTo(move(ids, index, index + 1)),
      setImage: (imageId) => {
        resetErrors()
        setImage.mutate({ params: { path }, body: { imageId, version: product.version } })
      },
      suspend: run(() => suspend.mutate({ params: { path } })),
      resume: run(() => resume.mutate({ params: { path } })),
      remove: run(() => remove.mutate({ params: { path, query: { version: product.version } } })),
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>バリアント</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {product.variants.length === 0 ? (
          <p className="text-muted-foreground text-sm">バリアントはまだありません。公開するには 1 つ以上必要です。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>バリアント品番</TableHead>
                {product.axes.map((axis) => (
                  <TableHead key={axis}>{axis}</TableHead>
                ))}
                <TableHead>画像</TableHead>
                <TableHead>販売</TableHead>
                <TableHead className="text-right">在庫数</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {product.variants.map((variant, index) => (
                <VariantRow
                  actions={rowActions(variant.id, index)}
                  axes={product.axes}
                  deletable={product.actions.deleteVariants}
                  first={index === 0}
                  imageOptions={imageOptions}
                  key={variant.id}
                  last={index === ids.length - 1}
                  pending={pending}
                  productId={product.id}
                  variant={variant}
                />
              ))}
            </TableBody>
          </Table>
        )}
        <ErrorMessage error={error} />
        {product.actions.addVariant ? (
          <AddVariantForm product={product} refetch={refetch} />
        ) : (
          <p className="text-muted-foreground text-sm">軸を持たない商品のバリアントは 1 つだけです。</p>
        )}
      </CardContent>
    </Card>
  )
}
