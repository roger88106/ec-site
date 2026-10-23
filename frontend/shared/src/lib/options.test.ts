import { describe, expect, test } from 'vitest'
import { withAllOption } from './options'

describe('「すべて」の選択肢', () => {
  test('選択肢の先頭に、値が null の「すべて」を付け、元の選択肢を順に続ける', () => {
    const options = [
      { value: 'A', label: '甲' },
      { value: 'B', label: '乙' },
    ]
    expect(withAllOption(options)).toEqual([{ value: null, label: 'すべて' }, ...options])
  })

  test('選択肢が空でも「すべて」だけを返す', () => {
    expect(withAllOption([])).toEqual([{ value: null, label: 'すべて' }])
  })

  test('元の配列を変えない', () => {
    const options = [{ value: 'A', label: '甲' }]
    withAllOption(options)
    expect(options).toEqual([{ value: 'A', label: '甲' }])
  })
})
