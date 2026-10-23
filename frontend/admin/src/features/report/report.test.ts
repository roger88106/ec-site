import { describe, expect, test } from 'vitest'
import { formatYenCompact } from '@/features/report/report'

describe('グラフの軸の金額', () => {
  test('グラフの軸の金額は、万・億で短くする', () => {
    expect(formatYenCompact(40000000)).toBe('4000万円')
    expect(formatYenCompact(3417808)).toBe('341.8万円')
    expect(formatYenCompact(5000)).toBe('5000円')
    expect(formatYenCompact(0)).toBe('0円')
  })
})
