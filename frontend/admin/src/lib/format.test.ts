import { describe, expect, test } from 'vitest'
import { formatNumber, fromDateTimeInput, toDateTimeInput } from '@/lib/format'

describe('数の整形', () => {
  test('数は 3 桁ごとに区切る。単位は付けない', () => {
    expect(formatNumber(1234)).toBe('1,234')
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatNumber(0)).toBe('0')
  })
})

describe('日時の入力（BR-3）', () => {
  test('BR-3 入力された日時は日本時間として扱い、オフセット付きで送る', () => {
    expect(fromDateTimeInput('2027-04-01T00:00')).toBe('2027-04-01T00:00:00+09:00')
  })

  test('空の入力は空白（null）にする', () => {
    expect(fromDateTimeInput('')).toBeNull()
  })

  test('BR-3 API の日時を、日本時間の入力欄の値にする', () => {
    expect(toDateTimeInput('2027-03-31T15:00:00Z')).toBe('2027-04-01T00:00')
    expect(toDateTimeInput(null)).toBe('')
  })

  test('入力欄の値に戻すと、元の日時と同じになる', () => {
    const iso = fromDateTimeInput('2028-04-01T00:00')
    expect(toDateTimeInput(iso)).toBe('2028-04-01T00:00')
  })
})
