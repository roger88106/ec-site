import { Button } from '@ec-site/shared/ui/button'
import { Link } from 'react-router'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { CreateTaxCategoryForm } from '@/features/tax/components/CreateTaxCategoryForm'
import { formatRate } from '@/lib/rate'

/** 税区分の一覧と登録（T706。US-1） */
export function TaxCategoryListPage() {
  const query = $api.useQuery('get', '/tax-categories')
  return (
    <>
      <PageHeader title="税区分" />
      <div className="max-w-4xl space-y-6">
        <CreateTaxCategoryForm />
        <QueryState query={query}>
          {({ items }) => (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>名称</TableHead>
                  <TableHead>現時点の率</TableHead>
                  <TableHead>税率の件数</TableHead>
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
                      <Button
                        nativeButton={false}
                        render={<Link to={paths.taxCategory(item.id)} />}
                        size="sm"
                        variant="outline"
                      >
                        税率を編集
                      </Button>
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
