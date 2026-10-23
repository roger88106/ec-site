import { LinkButton } from '@ec-site/shared/components/LinkButton'
import { keepPreviousData } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { paths } from '@/app/paths'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { QueryState } from '@/components/QueryState'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ProductFilterForm } from '@/features/product/components/ProductFilterForm'
import { StatusBadge } from '@/features/product/components/StatusBadge'
import { type ProductFilter, parseFilter, toSearchParams } from '@/features/product/productFilter'

/**
 * 商品の一覧。<br />
 * 絞り込みとページは URL のクエリに持ち、戻る・再読み込みで同じ一覧を出す。
 * 在庫数の合計は、表示中のページの商品について在庫の集計 API を呼んで組み合わせる。
 */
export function ProductListPage() {
  const t = useTranslations('product.list')
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseFilter(searchParams)
  const navigate = useNavigate()
  const query = $api.useQuery(
    'get',
    '/products',
    {
      params: {
        query: {
          code: filter.code || undefined,
          name: filter.name || undefined,
          status: filter.status ?? undefined,
          categoryId: filter.categoryId ?? undefined,
          page: filter.page,
        },
      },
    },
    // ページを移る間、前のページを表示したままにする
    { placeholderData: keepPreviousData },
  )
  const productIds = query.data?.items.map((item) => item.id) ?? []
  const stocks = $api.useQuery(
    'get',
    '/inventory/stock-summaries',
    { params: { query: { productIds } } },
    { enabled: productIds.length > 0 },
  )
  /** 商品 ID → 在庫数の合計（在庫の API の結果を、一覧の行に合わせる） */
  const totals = new Map(stocks.data?.items.map((item) => [item.productId, item.totalQuantity]))
  /** 絞り込みを URL に反映する（URL が絞り込みの正本。再読み込み・共有しても同じ一覧になる） */
  const apply = (next: ProductFilter) => setSearchParams(toSearchParams(next))

  return (
    <>
      <PageHeader title={t('title') /* 商品 */}>
        <LinkButton link={<Link to={paths.productNew()} />}>{t('create') /* 商品を登録 */}</LinkButton>
      </PageHeader>
      <div className="space-y-4">
        {/* 絞り込みを変えたら（URL が変わったら）入力欄を再作成する */}
        <ProductFilterForm filter={filter} key={searchParams.toString()} onApply={apply} />
        <QueryState query={query}>
          {(result) => (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('productCode') /* 商品品番 */}</TableHead>
                    <TableHead>{t('name') /* 商品名 */}</TableHead>
                    <TableHead>{t('status') /* 状態 */}</TableHead>
                    <TableHead className="text-right">{t('variantCount') /* バリアント数 */}</TableHead>
                    <TableHead className="text-right">{t('stockTotal') /* 在庫数の合計 */}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.items.map((item) => (
                    <TableRow className="cursor-pointer" key={item.id} onClick={() => navigate(paths.product(item.id))}>
                      <TableCell className="font-mono">
                        {/* 行のクリックでも同じ画面へ移るので、リンクのクリックを行に伝えない（二重に移らない） */}
                        <Link onClick={(event) => event.stopPropagation()} to={paths.product(item.id)}>
                          {item.productCode}
                        </Link>
                      </TableCell>
                      <TableCell>{item.name}</TableCell>
                      <TableCell>
                        <StatusBadge status={item.status} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{item.variantCount}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {/* 集計の取得に失敗しても、一覧は出す */}
                        {totals.get(item.id) ?? (stocks.isError ? t('stockUnavailable') /* 取得できない */ : '…')}
                      </TableCell>
                    </TableRow>
                  ))}
                  {result.items.length === 0 && (
                    <TableRow>
                      <TableCell className="text-center text-muted-foreground" colSpan={5}>
                        {t('empty') /* 該当なし */}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Pagination
                onChange={(page) => apply({ ...filter, page })}
                page={result.page}
                totalItems={result.totalItems}
                totalPages={result.totalPages}
              />
            </>
          )}
        </QueryState>
      </div>
    </>
  )
}
