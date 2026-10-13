import { Button } from '@ec-site/shared/ui/button'
import { Link, useParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { TaxRatesEditor } from '@/features/tax/components/TaxRatesEditor'
import { formatRate } from '@/lib/rate'

/** 税率の一覧の編集（T706。AC-2〜5、7） */
export function TaxRatesPage() {
  const t = useTranslations('tax.rates')
  const tCommon = useTranslations('common')
  const { taxCategoryId = '' } = useParams()
  const query = $api.useQuery('get', '/tax-categories/{taxCategoryId}', { params: { path: { taxCategoryId } } })
  return (
    <QueryState query={query}>
      {(taxCategory) => (
        <>
          <PageHeader title={t('title', { name: taxCategory.name }) /* 税区分の税率 */}>
            <Button nativeButton={false} render={<Link to={paths.taxCategories()} />} variant="outline">
              {tCommon('backToList') /* 一覧へ戻る */}
            </Button>
          </PageHeader>
          <div className="max-w-4xl space-y-4">
            <p className="text-muted-foreground text-sm">
              {t('currentRate', { rate: formatRate(taxCategory.currentRatePercent) }) /* 現時点の率 */}
            </p>
            <TaxRatesEditor refetch={query.refetch} taxCategory={taxCategory} />
          </div>
        </>
      )}
    </QueryState>
  )
}
