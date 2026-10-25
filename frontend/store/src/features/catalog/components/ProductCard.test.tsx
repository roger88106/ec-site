import { describe, expect, test } from 'vitest'
import type { ProductSummary } from '@/api/types'
import { ProductCard, ProductGrid } from '@/features/catalog/components/ProductCard'
import { renderStatic } from '@/test/render'

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
    const view = renderStatic(<ProductCard product={product()} />)

    expect(view.getByText('マグカップ')).toBeInTheDocument()
    expect(view.getByRole('img', { name: '商品の画像' })).toHaveAttribute('src', 'http://cdn.test/mug.webp')
    expect(view.getByText('1,100 円')).toBeInTheDocument()
    expect(view.getByText('（税抜 1,000 円）')).toBeInTheDocument()
    expect(view.getByRole('link')).toHaveAttribute('href', '/products/00000000-0000-0000-0000-000000000101')
    expect(view.queryByText('在庫なし')).not.toBeInTheDocument()
  })

  test('AC-56 すべてのバリアントが在庫切れの商品も出し、「在庫なし」と示す', () => {
    const view = renderStatic(<ProductCard product={product({ inStock: false })} />)

    expect(view.getByText('マグカップ')).toBeInTheDocument()
    expect(view.getByText('在庫なし')).toBeInTheDocument()
  })

  test('画像がなければ、画像を出さずに商品名を出す', () => {
    const view = renderStatic(<ProductCard product={product({ imageUrl: null })} />)

    expect(view.queryByRole('img')).not.toBeInTheDocument()
    expect(view.getByText('マグカップ')).toBeInTheDocument()
  })
})

describe('商品の並び', () => {
  test('商品を 1 件ずつ並べて出し、メッセージは出さない', () => {
    const view = renderStatic(
      <ProductGrid
        emptyMessage="ありません"
        products={[product(), product({ id: '00000000-0000-0000-0000-000000000102', name: '皿' })]}
      />,
    )

    expect(view.getAllByRole('listitem')).toHaveLength(2)
    expect(view.getByText('皿')).toBeInTheDocument()
    expect(view.queryByText('ありません')).not.toBeInTheDocument()
  })

  test('商品がなければ、渡したメッセージを出す', () => {
    const view = renderStatic(<ProductGrid emptyMessage="条件に合う商品はありません。" products={[]} />)

    expect(view.getByText('条件に合う商品はありません。')).toBeInTheDocument()
    expect(view.queryByRole('list')).not.toBeInTheDocument()
  })
})
