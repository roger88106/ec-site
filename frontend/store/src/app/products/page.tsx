import { flattenTree } from '@ec-site/shared/lib/categoryTree'
import type { Metadata } from 'next'
import { getApi } from '@/api/client'
import { unwrap } from '@/api/result'
import type { ProductSummaryPage } from '@/api/types'
import { PageLinks } from '@/components/PageLinks'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { ListFilterForm } from '@/features/catalog/components/ListFilterForm'
import { ProductGrid } from '@/features/catalog/components/ProductCard'
import { listHref, parseListQuery } from '@/features/catalog/listQuery'

/** ブラウザのタブの題名 */
export const metadata: Metadata = { title: '商品一覧' }

/**
 * 商品一覧（T806。AC-51〜57）。カテゴリ（子孫を含む）と税込の価格帯で絞り込み、並べ替え、ページで送る。<br />
 * 入力の誤り（URL のクエリの形が違うなど）は、API のエラーの message をこの画面に出す。
 */
export default async function ProductListPage({ searchParams }: PageProps<'/products'>) {
  const query = parseListQuery(await searchParams)
  const api = await getApi()
  const [categories, result] = await Promise.all([
    api.GET('/categories').then(unwrap),
    api.GET('/products', {
      params: {
        query: {
          categoryId: query.categoryId ?? undefined,
          minPrice: query.minPrice ?? undefined,
          maxPrice: query.maxPrice ?? undefined,
          sort: query.sort,
          page: query.page,
        },
      },
    }),
  ])
  const flatCategories = flattenTree(categories.items)
  const categoryName = flatCategories.find((category) => category.id === query.categoryId)?.name

  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">{categoryName ?? '商品一覧'}</h1>
      <ListFilterForm categories={flatCategories} query={query} />
      {result.response.status === 400 ? (
        <Alert variant="destructive">
          <AlertDescription>{result.error?.message ?? '条件を見直してください。'}</AlertDescription>
        </Alert>
      ) : (
        <ProductList hrefFor={(page) => listHref({ ...query, page })} page={unwrap(result)} />
      )}
    </div>
  )
}

/** 商品の並びとページ送り */
function ProductList({ page, hrefFor }: { page: ProductSummaryPage; hrefFor: (page: number) => string }) {
  return (
    <div className="space-y-6">
      <ProductGrid emptyMessage="条件に合う商品はありません。" products={page.items} />
      <PageLinks hrefFor={hrefFor} page={page.page} totalItems={page.totalItems} totalPages={page.totalPages} />
    </div>
  )
}
