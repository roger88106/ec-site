import { describe, expect, test } from 'vitest'
import type { CategoryNode } from '@/api/types'
import { CategoryList } from '@/features/catalog/components/CategoryList'
import { renderMarkup, renderStatic } from '@/test/render'

/** 最上位の「食器」と、その子の「マグカップ」。子のない最上位の「衣類」 */
const CATEGORIES: CategoryNode[] = [
  {
    id: 'c-tableware',
    name: '食器',
    children: [{ id: 'c-mug', name: 'マグカップ', children: [] }],
  },
  { id: 'c-clothes', name: '衣類', children: [] },
]

describe('カテゴリの一覧', () => {
  test('最上位のカテゴリと子のカテゴリを、カテゴリで絞り込んだ商品一覧へのリンクにする', () => {
    const view = renderStatic(<CategoryList categories={CATEGORIES} />)

    expect(view.getByRole('link', { name: '食器' })).toHaveAttribute('href', '/products?categoryId=c-tableware')
    expect(view.getByRole('link', { name: 'マグカップ' })).toHaveAttribute('href', '/products?categoryId=c-mug')
    expect(view.getByRole('link', { name: '衣類' })).toHaveAttribute('href', '/products?categoryId=c-clothes')
    expect(view.getAllByRole('link')).toHaveLength(3)
  })

  test('カテゴリがなければ、何も出さない', () => {
    expect(renderMarkup(<CategoryList categories={[]} />)).toBe('')
  })
})
