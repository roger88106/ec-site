import { describe, expect, test } from 'vitest'
import { type CategoryTreeNode, flattenTree } from './categoryTree'

/** カテゴリの木の節を作る */
const node = (id: string, children: CategoryTreeNode[] = []): CategoryTreeNode => ({ id, name: id, children })

describe('カテゴリの木', () => {
  test('深さ優先（親の直後に子孫）で並べ、深さを付ける', () => {
    const tree = [node('衣類', [node('トップス', [node('T シャツ')])]), node('食品')]
    expect(flattenTree(tree)).toEqual([
      { id: '衣類', name: '衣類', depth: 1 },
      { id: 'トップス', name: 'トップス', depth: 2 },
      { id: 'T シャツ', name: 'T シャツ', depth: 3 },
      { id: '食品', name: '食品', depth: 1 },
    ])
  })
})
