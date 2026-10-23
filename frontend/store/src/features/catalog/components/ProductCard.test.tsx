import { describe, expect, test } from 'vitest'
import type { ProductSummary } from '@/api/types'
import { ProductCard, ProductGrid } from '@/features/catalog/components/ProductCard'
import { findTag } from '@/test/markup'
import { renderMarkup } from '@/test/render'

/** 画像があり、在庫のある商品 */
function product(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    id: '00000000-0000-0000-0000-000000000101',
    name: 'マグカップ',
    imageUrl: 'http://cdn.test/mug.webp',
    price: { inclTax: 1100, exclTax: 1000 },
    inStock: true,
    ...overrides,
  }
}

describe('一覧の商品のカード', () => {
  test('AC-55 商品名、代表画像、価格（税込と税抜）を出し、商品の詳細へのリンクにする', () => {
    const html = renderMarkup(<ProductCard product={product()} />)

    expect(html).toContain('マグカップ')
    expect(findTag(html, 'img', { alt: '商品の画像' }).src).toBe('http://cdn.test/mug.webp')
    expect(html).toContain('1,100 円')
    expect(html).toContain('税抜 1,000 円')
    expect(html).toContain('href="/products/00000000-0000-0000-0000-000000000101"')
    expect(html).not.toContain('在庫なし')
  })

  test('AC-56 すべてのバリアントが在庫切れの商品も出し、「在庫なし」と示す', () => {
    const html = renderMarkup(<ProductCard product={product({ inStock: false })} />)

    expect(html).toContain('マグカップ')
    expect(html).toContain('在庫なし')
  })

  test('画像がなければ、画像を出さずに商品名を出す', () => {
    const html = renderMarkup(<ProductCard product={product({ imageUrl: null })} />)

    expect(html).not.toContain('<img')
    expect(html).toContain('マグカップ')
  })
})

describe('商品の並び', () => {
  test('商品を 1 件ずつ並べて出し、メッセージは出さない', () => {
    const html = renderMarkup(
      <ProductGrid
        emptyMessage="ありません"
        products={[product(), product({ id: '00000000-0000-0000-0000-000000000102', name: '皿' })]}
      />,
    )

    expect(html.match(/<li>/g)).toHaveLength(2)
    expect(html).toContain('皿')
    expect(html).not.toContain('ありません')
  })

  test('商品がなければ、渡したメッセージを出す', () => {
    const html = renderMarkup(<ProductGrid emptyMessage="条件に合う商品はありません。" products={[]} />)

    expect(html).toContain('条件に合う商品はありません。')
    expect(html).not.toContain('<ul')
  })
})
