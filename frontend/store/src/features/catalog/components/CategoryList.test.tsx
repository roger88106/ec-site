import { describe, expect, test } from 'vitest'
import type { CategoryNode } from '@/api/types'
import { CategoryList } from '@/features/catalog/components/CategoryList'
import { renderMarkup } from '@/test/render'

/** 最上位の「食器」と、その子の「マグカップ」 */
const CATEGORIES: CategoryNode[] = [
  {
    id: 'c-tableware',
    name: '食器',
    children: [{ id: 'c-mug', name: 'マグカップ', children: [] }],
  },
]

describe('カテゴリの一覧', () => {
  test('最上位のカテゴリと子のカテゴリを、カテゴリで絞り込んだ商品一覧へのリンクにする', () => {
    const html = renderMarkup(<CategoryList categories={CATEGORIES} />)

    expect(html).toMatch(/<a [^>]*href="\/products\?categoryId=c-tableware"[^>]*>食器<\/a>/)
    expect(html).toMatch(/<a [^>]*href="\/products\?categoryId=c-mug"[^>]*>マグカップ<\/a>/)
  })
})
