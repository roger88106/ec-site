import { describe, expect, test } from 'vitest'
import { deepEqual } from '@/lib/equal'

describe('値の深い比較', () => {
  test('入れ子の中身が同じなら true', () => {
    expect(deepEqual({ a: 1, b: [{ c: 'x' }] }, { b: [{ c: 'x' }], a: 1 })).toBe(true)
  })

  test('値、配列の長さ、キーが違えば false', () => {
    expect(deepEqual({ a: 1 }, { a: 2 })).toBe(false)
    expect(deepEqual([1, 2], [1])).toBe(false)
    expect(deepEqual({ a: 1 }, { b: 1 })).toBe(false)
    expect(deepEqual([], {})).toBe(false)
    expect(deepEqual(null, {})).toBe(false)
  })
})
