import { LinkButton } from '@ec-site/shared/components/LinkButton'
import { Link } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CreateTaxCategoryForm } from '@/features/tax/components/CreateTaxCategoryForm'
import { formatRate } from '@/lib/rate'

/** 税区分の一覧と登録 */
export function TaxCategoryListPage() {
  const t = useTranslations('tax.list')
  const query = $api.useQuery('get', '/tax-categories')
  return (
    <>
      <PageHeader title={t('title') /* 税区分 */} />
      <div className="max-w-4xl space-y-6">
        <CreateTaxCategoryForm />
        <QueryState query={query}>
          {({ items }) => (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('name') /* 名称 */}</TableHead>
                  <TableHead>{t('currentRate') /* 現時点の率 */}</TableHead>
                  <TableHead>{t('rateCount') /* 税率の件数 */}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell>{item.name}</TableCell>
                    <TableCell>{formatRate(item.currentRatePercent)}</TableCell>
                    <TableCell>{item.rates.length}</TableCell>
                    <TableCell className="text-right">
                      <LinkButton link={<Link to={paths.taxCategory(item.id)} />} size="sm" variant="outline">
                        {t('editRates') /* 税率を編集 */}
                      </LinkButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </QueryState>
      </div>
    </>
  )
}
