import { describe, expect, test } from 'vitest'
import { type CategoryTreeNode, categoryOptions, flattenTree } from './categoryTree'

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

describe('カテゴリの選択肢', () => {
  test('先頭に「すべて」（値は null）を置き、階層の深さだけ全角の空白で字下げする', () => {
    const categories = [
      { id: '衣類', name: '衣類', depth: 1 },
      { id: 'トップス', name: 'トップス', depth: 2 },
      { id: 'T シャツ', name: 'T シャツ', depth: 3 },
    ]
    expect(categoryOptions(categories)).toEqual([
      { value: null, label: 'すべて' },
      { value: '衣類', label: '衣類' },
      { value: 'トップス', label: '　トップス' },
      { value: 'T シャツ', label: '　　T シャツ' },
    ])
  })
})
