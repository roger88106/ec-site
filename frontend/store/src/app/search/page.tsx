import { Alert, AlertDescription } from '@ec-site/shared/ui/alert'
import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import type { Metadata } from 'next'
import { getTranslations } from 'next-intl/server'
import { getApi } from '@/api/client'
import { parameters } from '@/api/generated/constraints'
import { unwrap } from '@/api/result'
import { PageLinks } from '@/components/PageLinks'
import { ProductGrid } from '@/features/catalog/components/ProductCard'
import { parseSearchQuery, searchHref } from '@/features/catalog/searchQuery'

/** ブラウザーのタブの題名 */
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('catalog.search')
  return { title: t('title') } // 検索結果
}

/**
 * 検索結果。商品名または説明にキーワードを含む商品を、新着順で出す。<br />
 * キーワードが空なら API を呼ばず、入力を促す。
 */
export default async function SearchPage({ searchParams }: PageProps<'/search'>) {
  const t = await getTranslations('catalog.search')
  const query = parseSearchQuery(await searchParams)
  return (
    <div className="space-y-6">
      <h1 className="font-heading font-semibold text-2xl">
        {query.q ? t('heading', { q: query.q }) : t('prompt') /* 検索結果か、入力を促す */}
      </h1>
      <form action="/search" className="flex max-w-md gap-2">
        <Input
          aria-label={t('keyword') /* キーワード */}
          defaultValue={query.q}
          maxLength={parameters.searchProducts.q.maxLength}
          name="q"
          required
        />
        <Button type="submit">{t('submit') /* 検索 */}</Button>
      </form>
      {query.q ? (
        <SearchResult page={query.page} q={query.q} />
      ) : (
        <p className="text-muted-foreground">{t('help') /* 入力を促す */}</p>
      )}
    </div>
  )
}

/** キーワードで検索した商品の並びとページ送り */
async function SearchResult({ q, page }: { q: string; page: number }) {
  const t = await getTranslations('catalog.search')
  const api = await getApi()
  const result = await api.GET('/products/search', { params: { query: { q, page } } })
  if (result.response.status === 400) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{result.error?.message ?? t('invalidKeyword') /* キーワードの再検討 */}</AlertDescription>
      </Alert>
    )
  }
  const found = unwrap(result)
  return (
    <div className="space-y-6">
      <ProductGrid emptyMessage={t('empty') /* 該当なし */} products={found.items} />
      <PageLinks
        hrefFor={(target) => searchHref({ q, page: target })}
        page={found.page}
        totalItems={found.totalItems}
        totalPages={found.totalPages}
      />
    </div>
  )
}
