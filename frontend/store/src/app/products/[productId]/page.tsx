import { isUuid } from '@ec-site/shared/lib/patterns'
import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { getApi, UNCACHED } from '@/api/client'
import { unwrap } from '@/api/result'
import { PriceText } from '@/components/PriceText'
import { ProductShowcase } from '@/features/catalog/components/ProductShowcase'

/**
 * 商品を取得する。
 *
 * 表示されない商品・存在しない商品・形の違う ID は、どれも「見つかりません」にする。
 * 非公開にした直後から見つからなくなるよう、キャッシュを使わない（UNCACHED）。
 *
 * @param productId 商品 ID（URL の値）
 * @returns 商品
 */
async function fetchProduct(productId: string) {
  if (!isUuid(productId)) {
    notFound()
  }
  const api = await getApi()
  return unwrap(await api.GET('/products/{productId}', { params: { path: { productId } }, ...UNCACHED }))
}

/** ブラウザーのタブの題名を商品名にする（見つからなければ、本文と同じく「見つかりません」） */
export async function generateMetadata({ params }: PageProps<'/products/[productId]'>): Promise<Metadata> {
  const product = await fetchProduct((await params).productId)
  return { title: product.name }
}

/**
 * 商品の詳細
 *
 * URL は商品 ID で、品番が変わっても変わらない。
 */
export default async function ProductDetailPage({ params }: PageProps<'/products/[productId]'>) {
  const product = await fetchProduct((await params).productId)
  return (
    <ProductShowcase
      axes={product.axes}
      header={
        <>
          <h1 className="font-heading text-2xl font-semibold">{product.name}</h1>
          <PriceText price={product.price} size="lg" />
        </>
      }
      imageUrls={product.imageUrls}
      variants={product.variants}
    >
      {product.description && <p className="text-sm leading-relaxed whitespace-pre-wrap">{product.description}</p>}
    </ProductShowcase>
  )
}
