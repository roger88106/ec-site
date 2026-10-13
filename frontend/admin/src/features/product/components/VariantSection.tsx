import { Badge } from '@ec-site/shared/ui/badge'
import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { ArrowDownIcon, ArrowUpIcon } from 'lucide-react'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import { OptionSelect } from '@/components/OptionSelect'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AddVariantForm } from '@/features/product/components/AddVariantForm'
import { StockLink } from '@/features/product/components/StockLink'
import { isNeverShown } from '@/features/product/product'
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
  const neverShown = isNeverShown(product)
  // 軸を持たない商品のバリアントは 1 つだけ（AC-24）
  const canAdd = product.axes.length > 0 || product.variants.length === 0
  /** 表示している商品のバージョンで、バリアントの並びを送る */
  const reorderTo = (variantIds: string[]) => {
    resetErrors()
    reorder.mutate({
      params: { path: { productId: product.id } },
      body: { variantIds, version: product.version },
    })
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
                <TableRow key={variant.id}>
                  <TableCell className="font-mono">{variant.variantCode}</TableCell>
                  {product.axes.map((axis, axisIndex) => (
                    <TableCell key={axis}>{variant.axisValues[axisIndex]}</TableCell>
                  ))}
                  <TableCell>
                    <OptionSelect
                      ariaLabel={`${variant.variantCode}の画像`}
                      className="w-28"
                      onChange={(imageId) => {
                        resetErrors()
                        setImage.mutate({
                          params: { path: { productId: product.id, variantId: variant.id } },
                          body: { imageId, version: product.version },
                        })
                      }}
                      options={imageOptions}
                      value={variant.imageId ?? null}
                    />
                  </TableCell>
                  <TableCell>
                    {variant.salesSuspended ? <Badge variant="secondary">販売停止</Badge> : '販売中'}
                  </TableCell>
                  <TableCell className="text-right">
                    <StockLink productId={product.id} variantId={variant.id} />
                  </TableCell>
                  <TableCell className="space-x-1 whitespace-nowrap text-right">
                    <Button
                      aria-label={`${variant.variantCode}を上へ`}
                      disabled={index === 0 || pending}
                      onClick={() => reorderTo(move(ids, index, index - 1))}
                      size="icon-xs"
                      variant="ghost"
                    >
                      <ArrowUpIcon />
                    </Button>
                    <Button
                      aria-label={`${variant.variantCode}を下へ`}
                      disabled={index === ids.length - 1 || pending}
                      onClick={() => reorderTo(move(ids, index, index + 1))}
                      size="icon-xs"
                      variant="ghost"
                    >
                      <ArrowDownIcon />
                    </Button>
                    {/* 販売停止のバリアントも C 側の選択肢に出るが、選べない（AC-28） */}
                    {variant.salesSuspended ? (
                      <Button
                        aria-label={`${variant.variantCode}の販売を再開`}
                        disabled={pending}
                        onClick={() => {
                          resetErrors()
                          resume.mutate({ params: { path: { productId: product.id, variantId: variant.id } } })
                        }}
                        size="xs"
                        variant="outline"
                      >
                        販売を再開
                      </Button>
                    ) : (
                      <Button
                        aria-label={`${variant.variantCode}を販売停止`}
                        disabled={pending}
                        onClick={() => {
                          resetErrors()
                          suspend.mutate({ params: { path: { productId: product.id, variantId: variant.id } } })
                        }}
                        size="xs"
                        variant="outline"
                      >
                        販売停止
                      </Button>
                    )}
                    {/* C 側に表示されたことのある商品のバリアントは削除できない（AC-71） */}
                    {neverShown && (
                      <ConfirmDialog
                        confirmLabel="削除する"
                        description="在庫もあわせて削除します。"
                        onConfirm={() => {
                          resetErrors()
                          remove.mutate({
                            params: {
                              path: { productId: product.id, variantId: variant.id },
                              query: { version: product.version },
                            },
                          })
                        }}
                        title={`バリアント「${variant.variantCode}」を削除しますか？`}
                        trigger={
                          <Button disabled={pending} size="xs" variant="outline">
                            削除
                          </Button>
                        }
                      />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        <ErrorMessage error={error} />
        {canAdd ? (
          <AddVariantForm product={product} refetch={refetch} />
        ) : (
          <p className="text-muted-foreground text-sm">軸を持たない商品のバリアントは 1 つだけです。</p>
        )}
      </CardContent>
    </Card>
  )
}
