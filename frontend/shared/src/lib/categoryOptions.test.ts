import { describe, expect, test } from 'vitest'
import { categoryOptions } from './categoryTree'

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
