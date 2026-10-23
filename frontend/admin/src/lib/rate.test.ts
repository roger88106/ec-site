import { describe, expect, test } from 'vitest'
import { formatRate } from '@/lib/rate'

describe('税率の表示', () => {
  test('整数の税率は、小数点を付けずに % を後ろに付ける', () => {
    expect(formatRate(10)).toBe('10%')
    expect(formatRate(8)).toBe('8%')
  })

  test('小数の税率は、小数 2 桁まで表示する', () => {
    expect(formatRate(8.5)).toBe('8.5%')
    expect(formatRate(8.25)).toBe('8.25%')
    expect(formatRate(8.256)).toBe('8.26%')
  })

  test('0 は「0%」と表示する', () => {
    expect(formatRate(0)).toBe('0%')
  })

  test('空白（null、undefined）は「なし」と表示する', () => {
    expect(formatRate(null)).toBe('なし')
    expect(formatRate(undefined)).toBe('なし')
  })
})
