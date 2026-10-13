import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import type { Metadata } from 'next'
import { getApi } from '@/api/client'
import { parameters } from '@/api/generated/constraints'
import { unwrap } from '@/api/result'
import { PageLinks } from '@/components/PageLinks'
import { ProductGrid } from '@/features/catalog/components/ProductCard'
import { parseSearchQuery, searchHref } from '@/features/catalog/searchQuery'

/** ブラウザのタブの題名 */
export const metadata: Metadata = { title: '検索結果' }

/**
 * 検索結果（T807。AC-58〜61）。商品名または説明にキーワードを含む商品を、新着順で出す。<br />
 * キーワードが空なら API を呼ばず、入力を促す。
 */
export default async function SearchPage({ searchParams }: PageProps<'/search'>) {
  const query = parseSearchQuery(await searchParams)
  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">
        {query.q ? `「${query.q}」の検索結果` : 'キーワードで探す'}
      </h1>
      <form action="/search" className="flex max-w-md gap-2">
        <Input
          aria-label="キーワード"
          defaultValue={query.q}
          maxLength={parameters.searchProducts.q.maxLength}
          name="q"
          required
        />
        <Button type="submit">検索</Button>
      </form>
      {query.q ? (
        <SearchResult page={query.page} q={query.q} />
      ) : (
        <p className="text-muted-foreground">商品名や説明に含まれる言葉を入力してください。</p>
      )}
    </div>
  )
}

/** キーワードで検索した商品の並びとページ送り */
async function SearchResult({ q, page }: { q: string; page: number }) {
  const api = await getApi()
  const result = await api.GET('/products/search', { params: { query: { q, page } } })
  if (result.response.status === 400) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{result.error?.message ?? 'キーワードを見直してください。'}</AlertDescription>
      </Alert>
    )
  }
  const found = unwrap(result)
  return (
    <div className="space-y-6">
      <ProductGrid emptyMessage="キーワードに合う商品はありません。" products={found.items} />
      <PageLinks
        hrefFor={(target) => searchHref({ q, page: target })}
        page={found.page}
        totalItems={found.totalItems}
        totalPages={found.totalPages}
      />
    </div>
  )
}
