'use client'

import { useTranslations } from 'next-intl'
import { type ReactNode, useState } from 'react'
import type { Variant, VariantAxis } from '@/api/types'
import { ProductImage } from '@/components/ProductImage'
import { AddToCartForm } from '@/features/catalog/components/AddToCartForm'
import { VariantPicker } from '@/features/catalog/components/VariantPicker'
import { emptySelection, imageForVariant, selectedVariant } from '@/features/catalog/variantSelection'

/**
 * 商品の詳細の、画像とバリアントの選択と、カートに入れる操作。<br />
 * 大きな画像は代表画像から始め、バリアントを選ぶとその画像（指定がなければ代表画像）に、小さな画像を押すとその画像に替える。
 * カートに入れるのは、選んだバリアント（軸のない商品は、ただ 1 つのバリアント）。
 */
export function ProductShowcase({
  imageUrls,
  axes,
  variants,
  header,
  children,
}: {
  imageUrls: string[]
  axes: VariantAxis[]
  variants: Variant[]
  /** 商品名と価格 */
  header: ReactNode
  /** 説明など、バリアントの選択の下に出すもの */
  children?: ReactNode
}) {
  const t = useTranslations('catalog.showcase')
  const [shown, setShown] = useState(() => imageForVariant(imageUrls, null))
  const [selection, setSelection] = useState(() => emptySelection(axes))
  const chosen = selectedVariant(axes, variants, selection)
  return (
    <article className="grid gap-8 md:grid-cols-2">
      <div className="space-y-3">
        <div className="flex aspect-square items-center justify-center overflow-hidden rounded-lg bg-muted">
          <ProductImage alt={t('image') /* 商品の画像 */} iconSize="lg" src={shown} />
        </div>
        {imageUrls.length > 1 && (
          <ul className="grid grid-cols-5 gap-2">
            {imageUrls.map((url, index) => (
              <li key={url}>
                <button
                  aria-label={t('showImage', { number: index + 1 }) /* 画像を見る */}
                  aria-pressed={url === shown}
                  className="block aspect-square w-full overflow-hidden rounded-md border aria-pressed:ring-2 aria-pressed:ring-primary"
                  onClick={() => setShown(url)}
                  type="button"
                >
                  {/* biome-ignore lint/performance/noImgElement: 画像は配信の側（CDN）で最適化する */}
                  <img alt="" className="size-full object-cover" src={url} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="space-y-6">
        {header}
        <VariantPicker
          axes={axes}
          chosen={chosen}
          onChange={(next) => {
            // 再描画の前に、次の選択で決まるバリアントの画像に替えるため、ここでも計算する
            setSelection(next)
            setShown(imageForVariant(imageUrls, selectedVariant(axes, variants, next)))
          }}
          selection={selection}
          variants={variants}
        />
        <AddToCartForm variant={chosen} />
        {children}
      </div>
    </article>
  )
}
