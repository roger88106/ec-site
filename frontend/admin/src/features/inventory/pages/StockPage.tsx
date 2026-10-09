import { Link, useParams } from 'react-router'
import { $api } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { AdjustStockForm } from '@/features/inventory/components/AdjustStockForm'
import { StockHistory } from '@/features/inventory/components/StockHistory'

/** バリアントの在庫数、調整、履歴（T710。US-8） */
export function StockPage() {
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
            在庫：<span className="font-mono">{variant?.variantCode ?? '…'}</span>
          </span>
        }
      >
        <Button nativeButton={false} render={<Link to={`/products/${productId}`} />} variant="outline">
          商品へ戻る
        </Button>
      </PageHeader>
      <div className="max-w-4xl space-y-6">
        {product.data && variant && (
          <p className="text-muted-foreground text-sm">
            {product.data.name}
            {axes.length > 0 && `（${axes.map((axis, index) => `${axis}：${variant.axisValues[index]}`).join('、')}）`}
          </p>
        )}
        <Card>
          <CardHeader>
            <CardTitle>在庫数</CardTitle>
          </CardHeader>
          <CardContent>
            <QueryState query={stock}>
              {(data) => <p className="font-semibold text-3xl tabular-nums">{data.quantity}</p>}
            </QueryState>
          </CardContent>
        </Card>
        <AdjustStockForm variantId={variantId} />
        <StockHistory variantId={variantId} />
      </div>
    </>
  )
}
