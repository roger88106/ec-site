import { describe, expect, test } from 'vitest'
import { ListFilterForm } from '@/features/catalog/components/ListFilterForm'
import { renderStatic } from '@/test/render'

/** 衣類とその子のシャツ */
const CLOTHES = '00000000-0000-0000-0000-000000000301'
const SHIRTS = '00000000-0000-0000-0000-000000000302'

describe('商品一覧の絞り込み', () => {
  test('URL のクエリの条件を初めの値にし、商品一覧へ GET で送る', () => {
    const view = renderStatic(
      <ListFilterForm
        categories={[
          { id: CLOTHES, name: '衣類', depth: 1 },
          { id: SHIRTS, name: 'シャツ', depth: 2 },
        ]}
        query={{ categoryId: SHIRTS, minPrice: 1000, maxPrice: null, sort: 'price_asc', page: 2 }}
      />,
    )

    const category = view.getByRole<HTMLSelectElement>('combobox', { name: 'カテゴリ' })
    // form は要素の位置でなく、入力欄が属するフォーム（フォームの所有者）を返す
    expect(category.form).toHaveAttribute('action', '/products')
    expect(category).toHaveValue(SHIRTS)
    expect(category).toHaveDisplayValue('　シャツ')
    expect(view.getByRole('option', { name: '衣類' })).toHaveAttribute('value', CLOTHES)
    const sort = view.getByRole('combobox', { name: '並び順' })
    expect(sort).toHaveValue('price_asc')
    expect(sort).toHaveDisplayValue('価格の安い順')
    expect(view.getByRole('spinbutton', { name: '下限' })).toHaveAttribute('name', 'minPrice')
    expect(view.getByRole('spinbutton', { name: '下限' })).toHaveValue(1000)
    expect(view.getByRole('spinbutton', { name: '上限' })).toHaveAttribute('name', 'maxPrice')
    expect(view.getByRole('spinbutton', { name: '上限' })).toHaveValue(null)
    // ページは送らない（絞り込むと 1 ページ目に戻る）
    expect(category.form?.elements.namedItem('page')).toBeNull()
  })

  test('条件がなければ、すべてのカテゴリと新着順を選ぶ', () => {
    const view = renderStatic(
      <ListFilterForm
        categories={[]}
        query={{ categoryId: null, minPrice: null, maxPrice: null, sort: 'newest', page: 1 }}
      />,
    )

    const category = view.getByRole('combobox', { name: 'カテゴリ' })
    expect(category).toHaveValue('')
    expect(category).toHaveDisplayValue('すべて')
    const sort = view.getByRole('combobox', { name: '並び順' })
    expect(sort).toHaveValue('newest')
    expect(sort).toHaveDisplayValue('新着順')
  })
})
