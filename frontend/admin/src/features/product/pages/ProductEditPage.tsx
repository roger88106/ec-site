import { Link, useParams } from 'react-router'
import { $api } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { BasicInfoCard } from '@/features/product/components/BasicInfoCard'
import { ProductStatusCard } from '@/features/product/components/ProductStatusCard'
import { StatusBadge } from '@/features/product/components/StatusBadge'
import { VariantSection } from '@/features/product/components/VariantSection'

/** 商品の編集（T709。US-3〜5）。状態、基本情報、バリアントのカードを並べる */
export function ProductEditPage() {
  const { productId = '' } = useParams()
  const query = $api.useQuery('get', '/products/{productId}', { params: { path: { productId } } })
  return (
    <QueryState query={query}>
      {(product) => (
        <>
          <PageHeader
            title={
              <span className="flex items-center gap-3">
                <span className="font-mono">{product.productCode}</span>
                <StatusBadge status={product.status} />
              </span>
            }
          >
            <Button nativeButton={false} render={<Link to="/products" />} variant="outline">
              一覧へ戻る
            </Button>
          </PageHeader>
          <div className="max-w-4xl space-y-6">
            {/* 商品が変わったら（バージョンが変わったら）、前の操作のエラーを消す */}
            <ProductStatusCard key={product.version} product={product} />
            <BasicInfoCard product={product} refetch={query.refetch} />
            <VariantSection product={product} refetch={query.refetch} />
          </div>
        </>
      )}
    </QueryState>
  )
}
