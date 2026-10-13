import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { useTranslations } from 'use-intl'
import type { ApiPathPrefix } from '@/api/invalidate'
import type { ProductDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { paths } from '@/app/paths'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DateTime } from '@/components/DateTime'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Price } from '@/components/Price'

/** 価格・日時の表示と、公開・非公開・販売終了・削除（AC-29〜33） */
export function ProductStatusCard({ product }: { product: ProductDetail }) {
  const navigate = useNavigate()
  const t = useTranslations('product.status')
  const tCommon = useTranslations('common')
  const queryClient = useQueryClient()
  const path = { params: { path: { productId: product.id } } }
  /** 状態で在庫の画面の表示も変わるので、在庫も読み直す */
  const invalidate: ApiPathPrefix[] = ['/products', '/inventory']
  const publish = useApiMutation('put', '/products/{productId}/publish', {
    success: t('published') /* 公開の知らせ */,
    invalidate,
  })
  const unpublish = useApiMutation('put', '/products/{productId}/unpublish', {
    success: t('unpublished'), // 非公開の知らせ
    invalidate,
  })
  const discontinue = useApiMutation('put', '/products/{productId}/discontinue', {
    success: t('discontinued'), // 販売終了の知らせ
    invalidate,
  })
  const remove = useApiMutation('delete', '/products/{productId}', {
    success: t('deleted'), // 削除の知らせ
    invalidate,
    // 先に一覧へ移り、削除した商品の詳細を取得し直さない（404 になるため。管理画面の構成 第 4 節）
    onSuccess: () => {
      navigate(paths.products())
      queryClient.removeQueries({ queryKey: ['get', '/products/{productId}'] })
    },
  })
  const { pending, error } = useMutationGroup(publish, unpublish, discontinue, remove)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('title') /* 状態 */}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-muted-foreground">{t('currentPrice') /* 現時点の価格 */}</dt>
          <dd>
            <Price exclTax={product.priceExclTax} inclTax={product.priceInclTax} />
          </dd>
          <dt className="text-muted-foreground">{t('firstShownAt') /* 初回表示日時 */}</dt>
          <dd>
            <DateTime placeholder={t('notShownYet') /* まだ表示されていない */} value={product.firstShownAt} />
          </dd>
          <dt className="text-muted-foreground">{t('createdAt') /* 登録日時 */}</dt>
          <dd>
            <DateTime value={product.createdAt} />
          </dd>
          <dt className="text-muted-foreground">{t('updatedAt') /* 更新日時 */}</dt>
          <dd>
            <DateTime value={product.updatedAt} />
          </dd>
        </dl>
        <div className="flex flex-wrap gap-2">
          {product.actions.publish && (
            <Button disabled={pending} onClick={() => publish.mutate(path)}>
              {t('publish') /* 公開する */}
            </Button>
          )}
          {product.actions.unpublish && (
            <Button disabled={pending} onClick={() => unpublish.mutate(path)} variant="outline">
              {t('unpublish') /* 非公開にする */}
            </Button>
          )}
          {product.actions.discontinue && (
            <ConfirmDialog
              confirmLabel={t('discontinue') /* 販売終了にする */}
              description={t('discontinueDescription') /* 戻せない */}
              onConfirm={() => discontinue.mutate(path)}
              title={t('discontinueTitle', { name: product.name }) /* 販売終了の確認 */}
              trigger={
                <Button disabled={pending} variant="outline">
                  {t('discontinue') /* 販売終了にする */}
                </Button>
              }
            />
          )}
          {product.actions.delete && (
            <ConfirmDialog
              confirmLabel={tCommon('deleteConfirm') /* 削除する */}
              description={t('deleteDescription') /* あわせて消すもの */}
              onConfirm={() =>
                remove.mutate({
                  params: { path: { productId: product.id }, query: { version: product.version } },
                })
              }
              title={t('deleteTitle', { name: product.name }) /* 削除の確認 */}
              trigger={
                <Button disabled={pending} variant="destructive">
                  {tCommon('delete') /* 削除 */}
                </Button>
              }
            />
          )}
        </div>
        {product.status === 'DISCONTINUED' && (
          <p className="text-muted-foreground text-sm">{t('discontinuedNote') /* 公開に戻せない */}</p>
        )}
        {!product.actions.delete && (
          <p className="text-muted-foreground text-sm">{t('shownNote') /* 削除できない理由 */}</p>
        )}
        <ErrorMessage error={error} />
      </CardContent>
    </Card>
  )
}
