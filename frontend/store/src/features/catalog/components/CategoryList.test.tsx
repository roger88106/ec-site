// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { CategoryNode } from '@/api/types'
import { CategoryList } from '@/features/catalog/components/CategoryList'
import { renderInBrowser } from '@/test/render'

/** 最上位の「食器」と、その子の「マグカップ」 */
const CATEGORIES: CategoryNode[] = [
  {
    id: 'c-tableware',
    name: '食器',
    children: [{ id: 'c-mug', name: 'マグカップ', children: [] }],
  },
]

/** リンクの href */
function hrefOf(name: string): string | null {
  return screen.getByRole('link', { name }).getAttribute('href')
}

describe('カテゴリの一覧', () => {
  test('最上位のカテゴリと子のカテゴリを、カテゴリで絞り込んだ商品一覧へのリンクにする', () => {
    renderInBrowser(<CategoryList categories={CATEGORIES} />)

    expect(hrefOf('食器')).toBe('/products?categoryId=c-tableware')
    expect(hrefOf('マグカップ')).toBe('/products?categoryId=c-mug')
  })
})
