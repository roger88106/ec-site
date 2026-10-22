// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { ProductSummary } from '@/api/types'
import { ProductCard, ProductGrid } from '@/features/catalog/components/ProductCard'
import { renderInBrowser } from '@/test/render'

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

describe('一覧の商品', () => {
  test('AC-55 商品名、代表画像、価格（税込と税抜）を出し、商品の詳細へのリンクにする', () => {
    renderInBrowser(<ProductCard product={product()} />)

    expect(screen.getByText('マグカップ')).toBeDefined()
    expect(screen.getByRole('img', { name: '商品の画像' }).getAttribute('src')).toBe('http://cdn.test/mug.webp')
    expect(screen.getByText('1,100 円')).toBeDefined()
    expect(screen.getByText('（税抜 1,000 円）')).toBeDefined()
    expect(screen.getByRole('link').getAttribute('href')).toBe('/products/00000000-0000-0000-0000-000000000101')
    expect(screen.queryByText('在庫なし')).toBeNull()
  })

  test('AC-56 すべてのバリアントが在庫切れの商品も出し、「在庫なし」と示す', () => {
    renderInBrowser(<ProductCard product={product({ inStock: false })} />)

    expect(screen.getByText('マグカップ')).toBeDefined()
    expect(screen.getByText('在庫なし')).toBeDefined()
  })

  test('画像がなければ、代わりの図を出す', () => {
    renderInBrowser(<ProductCard product={product({ imageUrl: null })} />)

    expect(screen.queryByRole('img')).toBeNull()
    expect(document.querySelector('svg')).not.toBeNull()
  })
})

describe('商品の並び', () => {
  test('商品を並べる', () => {
    renderInBrowser(
      <ProductGrid
        emptyMessage="ありません"
        products={[product(), product({ id: '00000000-0000-0000-0000-000000000102', name: '皿' })]}
      />,
    )

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('皿')).toBeDefined()
    expect(screen.queryByText('ありません')).toBeNull()
  })

  test('商品がなければ、渡したメッセージを出す', () => {
    renderInBrowser(<ProductGrid emptyMessage="条件に合う商品はありません。" products={[]} />)

    expect(screen.getByText('条件に合う商品はありません。')).toBeDefined()
    expect(screen.queryByRole('list')).toBeNull()
  })
})
