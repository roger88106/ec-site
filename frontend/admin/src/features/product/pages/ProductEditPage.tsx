import { Button } from '@ec-site/shared/ui/button'
import { Link, useParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { BasicInfoCard } from '@/features/product/components/BasicInfoCard'
import { ProductImagesCard } from '@/features/product/components/ProductImagesCard'
import { ProductStatusCard } from '@/features/product/components/ProductStatusCard'
import { PublicationPeriodCard } from '@/features/product/components/PublicationPeriodCard'
import { StatusBadge } from '@/features/product/components/StatusBadge'
import { VariantSection } from '@/features/product/components/VariantSection'

/** 商品の編集（T709。US-3〜5）。状態、基本情報、バリアントのカードを並べる */
export function ProductEditPage() {
  const tCommon = useTranslations('common')
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
            <Button nativeButton={false} render={<Link to={paths.products()} />} variant="outline">
              {tCommon('backToList') /* 一覧へ戻る */}
            </Button>
          </PageHeader>
          <div className="max-w-4xl space-y-6">
            {/* 商品が変わったら（バージョンが変わったら）、前の操作のエラーを消す */}
            <ProductStatusCard key={product.version} product={product} />
            <PublicationPeriodCard product={product} refetch={query.refetch} />
            <BasicInfoCard product={product} refetch={query.refetch} />
            <ProductImagesCard product={product} />
            <VariantSection product={product} refetch={query.refetch} />
          </div>
        </>
      )}
    </QueryState>
  )
}
