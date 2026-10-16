import { Button } from '@ec-site/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ec-site/shared/ui/card'
import { Link, useParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { AdjustStockForm } from '@/features/inventory/components/AdjustStockForm'
import { StockHistory } from '@/features/inventory/components/StockHistory'

/** バリアントの在庫数、調整、履歴 */
export function StockPage() {
  const t = useTranslations('inventory.page')
  const tCommon = useTranslations('common')
  const { productId = '', variantId = '' } = useParams()
  const product = $api.useQuery('get', '/products/{productId}', { params: { path: { productId } } })
  const stock = $api.useQuery('get', '/inventory/variants/{variantId}', { params: { path: { variantId } } })
  const variant = product.data?.variants.find((item) => item.id === variantId)
  const axes = product.data?.axes ?? []

  return (
    <>
      <PageHeader
        title={
          <span>
            {t('titlePrefix') /* 在庫 */}
            <span className="font-mono">{variant?.variantCode ?? '…'}</span>
          </span>
        }
      >
        <Button
          nativeButton={false} /* a を描くので、button の振る舞いを外す */
          render={<Link to={paths.product(productId)} />}
          variant="outline"
        >
          {t('backToProduct') /* 商品へ戻る */}
        </Button>
      </PageHeader>
      <div className="max-w-4xl space-y-6">
        {product.data && variant && (
          <p className="text-muted-foreground text-sm">
            {product.data.name}
            {
              axes.length > 0 &&
                t('axes', {
                  values: axes
                    .map((axis, index) => tCommon('axisValue', { name: axis, value: variant.axisValues[index] }))
                    .join(tCommon('listSeparator')),
                }) /* 軸の名前と値 */
            }
          </p>
        )}
        <Card>
          <CardHeader>
            <CardTitle>{t('quantity') /* 在庫数 */}</CardTitle>
          </CardHeader>
          <CardContent>
            <QueryState query={stock}>
              {(data) => (
                <p aria-label={t('quantity')} className="font-semibold text-3xl tabular-nums" role="status">
                  {data.quantity}
                </p>
              )}
            </QueryState>
          </CardContent>
        </Card>
        <AdjustStockForm variantId={variantId} />
        <StockHistory variantId={variantId} />
      </div>
    </>
  )
}
