import Link from 'next/link'
import { getApi } from '@/api/client'
import { unwrap } from '@/api/result'
import { CategoryList } from '@/features/catalog/components/CategoryList'
import { ProductGrid } from '@/features/catalog/components/ProductCard'

/** トップに出す新着の商品の数 */
const NEW_ARRIVALS = 8

/** トップ（T805）。カテゴリの一覧と、新着の商品 */
export default async function HomePage() {
  const api = await getApi()
  const [categories, newest] = await Promise.all([
    api.GET('/categories').then(unwrap),
    api.GET('/products', { params: { query: { sort: 'newest', size: NEW_ARRIVALS } } }).then(unwrap),
  ])
  return (
    <div className="space-y-10">
      <section className="space-y-4">
        <h2 className="font-heading font-semibold text-xl">カテゴリ</h2>
        <CategoryList categories={categories.items} />
      </section>
      <section className="space-y-4">
        <div className="flex items-baseline justify-between">
          <h2 className="font-heading font-semibold text-xl">新着の商品</h2>
          <Link className="text-sm hover:underline" href="/products">
            すべての商品を見る
          </Link>
        </div>
        <ProductGrid emptyMessage="商品はまだありません。" products={newest.items} />
      </section>
    </div>
  )
}
