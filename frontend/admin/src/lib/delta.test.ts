import { describe, expect, test } from 'vitest'
import { formatDelta } from '@/lib/delta'

describe('在庫の増減量の表示', () => {
  test('AC-42 増加は + を付け、減少は - を付ける', () => {
    expect(formatDelta(10)).toBe('+10')
    expect(formatDelta(-3)).toBe('-3')
  })

  test('3 桁ごとに区切る', () => {
    expect(formatDelta(1200)).toBe('+1,200')
  })
})
