import { describe, expect, test } from 'vitest'
import { isDateString } from '@/lib/date'

describe('日付の形の判定', () => {
  test('日付の形（yyyy-MM-dd）か。実在する日付かは見ない', () => {
    expect(isDateString('2027-04-01')).toBe(true)
    expect(isDateString('2027-02-30')).toBe(true)
    expect(isDateString('2027-4-1')).toBe(false)
    expect(isDateString('2027/04/01')).toBe(false)
  })
})
