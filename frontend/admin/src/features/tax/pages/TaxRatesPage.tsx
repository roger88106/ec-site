import { Link, useParams } from 'react-router'
import { $api } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { TaxRatesEditor } from '@/features/tax/components/TaxRatesEditor'
import { formatRate } from '@/lib/rate'

/** 税率の一覧の編集（T706。AC-2〜5、7） */
export function TaxRatesPage() {
  const { taxCategoryId = '' } = useParams()
  const query = $api.useQuery('get', '/tax-categories/{taxCategoryId}', { params: { path: { taxCategoryId } } })
  return (
    <QueryState query={query}>
      {(taxCategory) => (
        <>
          <PageHeader title={`税区分「${taxCategory.name}」の税率`}>
            <Button nativeButton={false} render={<Link to="/tax-categories" />} variant="outline">
              一覧へ戻る
            </Button>
          </PageHeader>
          <div className="max-w-4xl space-y-4">
            <p className="text-muted-foreground text-sm">現時点の率：{formatRate(taxCategory.currentRatePercent)}</p>
            <TaxRatesEditor refetch={query.refetch} taxCategory={taxCategory} />
          </div>
        </>
      )}
    </QueryState>
  )
}
