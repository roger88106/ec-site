import { describe, expect, test } from 'vitest'
import type { CategoryNode } from '@/api/types'
import { flattenTree } from '@/lib/categoryTree'

/** カテゴリの木の節を作る */
const node = (id: string, depth: number, children: CategoryNode[] = []): CategoryNode => ({
  id,
  name: id,
  depth,
  displayOrder: 0,
  version: 0,
  children,
})

describe('カテゴリの木', () => {
  test('深さ優先（親の直後に子孫）で並べる', () => {
    const tree = [node('衣類', 1, [node('トップス', 2, [node('T シャツ', 3)]), node('ボトムス', 2)]), node('食品', 1)]
    expect(flattenTree(tree).map((item) => item.id)).toEqual(['衣類', 'トップス', 'T シャツ', 'ボトムス', '食品'])
  })
})
