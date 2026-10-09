import { describe, expect, test } from 'vitest'
import { move } from '@/lib/array'

describe('並び替え', () => {
  test('1 つ上へ移す', () => {
    expect(move(['a', 'b', 'c'], 2, 1)).toEqual(['a', 'c', 'b'])
  })

  test('1 つ下へ移す', () => {
    expect(move(['a', 'b', 'c'], 0, 1)).toEqual(['b', 'a', 'c'])
  })

  test('元の一覧は変えない', () => {
    const items = ['a', 'b']
    move(items, 0, 1)
    expect(items).toEqual(['a', 'b'])
  })
})
