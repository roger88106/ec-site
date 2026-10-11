import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { getApi, UNCACHED } from '@/api/client'
import { unwrap } from '@/api/result'
import { PriceText } from '@/components/PriceText'
import { ProductShowcase } from '@/features/catalog/components/ProductShowcase'
import { isUuid } from '@/lib/uuid'

/**
 * 商品を取得する。表示されない商品・存在しない商品・形の違う ID は、どれも「見つかりません」にする（AC-65）。<br />
 * 非公開にした直後から見つからなくなるよう、キャッシュを使わない（UNCACHED）。
 */
async function fetchProduct(productId: string) {
  if (!isUuid(productId)) {
    notFound()
  }
  const api = await getApi()
  return unwrap(await api.GET('/products/{productId}', { params: { path: { productId } }, ...UNCACHED }))
}

/** ブラウザのタブの題名を商品名にする（見つからなければ、本文と同じく「見つかりません」） */
export async function generateMetadata({ params }: PageProps<'/products/[productId]'>): Promise<Metadata> {
  const product = await fetchProduct((await params).productId)
  return { title: product.name }
}

/** 商品の詳細（T808。AC-62〜66）。URL は商品 ID で、品番が変わっても変わらない */
export default async function ProductDetailPage({ params }: PageProps<'/products/[productId]'>) {
  const product = await fetchProduct((await params).productId)
  return (
    <ProductShowcase
      axes={product.axes}
      header={
        <>
          <h1 className="font-heading font-semibold text-2xl">{product.name}</h1>
          <PriceText price={product.price} size="lg" />
        </>
      }
      imageUrls={product.imageUrls}
      variants={product.variants}
    >
      {product.description && <p className="whitespace-pre-wrap text-sm leading-relaxed">{product.description}</p>}
    </ProductShowcase>
  )
}
