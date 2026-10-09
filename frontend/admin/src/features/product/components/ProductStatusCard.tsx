import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { useInvalidate } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DateTime } from '@/components/DateTime'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Price } from '@/components/Price'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { isNeverShown } from '@/features/product/product'

/** 価格・日時の表示と、公開・非公開・販売終了・削除（AC-29〜33） */
export function ProductStatusCard({ product }: { product: ProductDetail }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const invalidate = useInvalidate()
  const path = { params: { path: { productId: product.id } } }
  const onChanged = (message: string) => async () => {
    toast.success(message)
    await invalidate('/products', '/inventory')
  }
  const publish = $api.useMutation('put', '/products/{productId}/publish', { onSuccess: onChanged('公開しました') })
  const unpublish = $api.useMutation('put', '/products/{productId}/unpublish', {
    onSuccess: onChanged('非公開にしました'),
  })
  const discontinue = $api.useMutation('put', '/products/{productId}/discontinue', {
    onSuccess: onChanged('販売終了にしました'),
  })
  const remove = $api.useMutation('delete', '/products/{productId}', {
    // 先に一覧へ移り、削除した商品の詳細を取得し直さない（404 になるため。管理画面の構成 第 4 節）
    onSuccess: async () => {
      toast.success('商品を削除しました')
      navigate('/products')
      queryClient.removeQueries({ queryKey: ['get', '/products/{productId}'] })
      await invalidate('/products', '/inventory')
    },
  })
  const mutations = [publish, unpublish, discontinue, remove]
  const pending = mutations.some((mutation) => mutation.isPending)
  const error = mutations.find((mutation) => mutation.error)?.error
  const neverShown = isNeverShown(product)

  return (
    <Card>
      <CardHeader>
        <CardTitle>状態</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-muted-foreground">現時点の価格</dt>
          <dd>
            <Price exclTax={product.priceExclTax} inclTax={product.priceInclTax} />
          </dd>
          <dt className="text-muted-foreground">初回表示日時</dt>
          <dd>
            <DateTime placeholder="まだ表示されていません" value={product.firstShownAt} />
          </dd>
          <dt className="text-muted-foreground">登録日時</dt>
          <dd>
            <DateTime value={product.createdAt} />
          </dd>
          <dt className="text-muted-foreground">更新日時</dt>
          <dd>
            <DateTime value={product.updatedAt} />
          </dd>
        </dl>
        <div className="flex flex-wrap gap-2">
          {product.status === 'UNPUBLISHED' && (
            <Button disabled={pending} onClick={() => publish.mutate(path)}>
              公開する
            </Button>
          )}
          {product.status === 'PUBLISHED' && (
            <Button disabled={pending} onClick={() => unpublish.mutate(path)} variant="outline">
              非公開にする
            </Button>
          )}
          {product.status !== 'DISCONTINUED' && (
            <ConfirmDialog
              confirmLabel="販売終了にする"
              description="販売終了にすると、公開に戻せません。"
              onConfirm={() => discontinue.mutate(path)}
              title={`商品「${product.name}」を販売終了にしますか？`}
              trigger={
                <Button disabled={pending} variant="outline">
                  販売終了にする
                </Button>
              }
            />
          )}
          {neverShown && (
            <ConfirmDialog
              confirmLabel="削除する"
              description="バリアントと在庫もあわせて削除します。"
              onConfirm={() =>
                remove.mutate({
                  params: { path: { productId: product.id }, query: { version: product.version } },
                })
              }
              title={`商品「${product.name}」を削除しますか？`}
              trigger={
                <Button disabled={pending} variant="destructive">
                  削除
                </Button>
              }
            />
          )}
        </div>
        {product.status === 'DISCONTINUED' && (
          <p className="text-muted-foreground text-sm">販売終了の商品は、公開に戻せません。</p>
        )}
        {!neverShown && (
          <p className="text-muted-foreground text-sm">
            店舗に表示されたことがあるため、削除できません。取り扱いをやめるときは販売終了にしてください。
          </p>
        )}
        <ErrorMessage error={error} />
      </CardContent>
    </Card>
  )
}
