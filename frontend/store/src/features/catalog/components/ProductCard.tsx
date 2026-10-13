import { Badge } from '@ec-site/shared/ui/badge'
import { ImageIcon } from 'lucide-react'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import type { ProductSummary } from '@/api/types'
import { PriceText } from '@/components/PriceText'

/** 一覧の商品（AC-55、56）。商品名、代表画像、価格を出し、在庫がなければ「在庫なし」と示す */
export function ProductCard({ product }: { product: ProductSummary }) {
  const t = useTranslations('catalog.stock')
  return (
    <Link className="group block space-y-2" href={`/products/${product.id}`}>
      <div className="relative flex aspect-square items-center justify-center overflow-hidden rounded-lg bg-muted">
        {product.imageUrl ? (
          // biome-ignore lint/performance/noImgElement: 画像は配信の側（CDN）で最適化する（plan 001 第 7.4 節）
          <img alt="" className="size-full object-cover" src={product.imageUrl} />
        ) : (
          // 画像がなければ、代わりの図を出す
          <ImageIcon aria-hidden className="size-10 text-muted-foreground" />
        )}
        {!product.inStock && (
          <Badge className="absolute top-2 left-2" variant="secondary">
            {t('unavailable') /* 在庫なし */}
          </Badge>
        )}
      </div>
      <div className="font-medium group-hover:underline">{product.name}</div>
      <PriceText price={product.price} />
    </Link>
  )
}

/** 商品の並び。商品がなければ message を出す */
export function ProductGrid({ products, emptyMessage }: { products: ProductSummary[]; emptyMessage: string }) {
  if (products.length === 0) {
    return <p className="text-muted-foreground">{emptyMessage}</p>
  }
  return (
    <ul className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-4">
      {products.map((product) => (
        <li key={product.id}>
          <ProductCard product={product} />
        </li>
      ))}
    </ul>
  )
}
