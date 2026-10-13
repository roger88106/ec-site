import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { findLiterals } from './input-constraints.mjs'

describe('入力の制約の数字の検査', () => {
  test('長さと範囲の属性に、数字を直接書いた行を見つける', () => {
    const text = [
      '<Input maxLength={50} />',
      '<Input min="0" />',
      '<Input max={ -1 } />',
      '<Input minLength={1} />',
    ].join('\n')
    assert.deepEqual(
      findLiterals(text).map((hit) => hit.line),
      [1, 2, 3, 4],
    )
  })

  test('定数を使った属性と、ほかの属性の数字は見つけない', () => {
    const text = [
      '<Input maxLength={schemas.CreateTaxCategoryRequest.name.maxLength} />',
      '<Input min={parameters.listProducts.minPrice.minimum} step={1} />',
      '<Textarea rows={2} />',
      'const minimum = 0',
    ].join('\n')
    assert.deepEqual(findLiterals(text), [])
  })
})
