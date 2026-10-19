import { describe, expect, test } from 'vitest'
import { formatPrice, fromDateTimeInput, toDateTimeInput } from '@/lib/format'

describe('価格の表示（BR-4）', () => {
  test('BR-4 税込価格を主に、税抜価格を括弧で併記する', () => {
    expect(formatPrice(1980, 1800)).toBe('1,980 円（税抜 1,800 円）')
  })

  test('BR-4 3 桁未満と 7 桁以上も、3 桁ごとに区切る', () => {
    expect(formatPrice(110, 100)).toBe('110 円（税抜 100 円）')
    expect(formatPrice(1100000, 1000000)).toBe('1,100,000 円（税抜 1,000,000 円）')
  })

  test('有効な税率がなく税込価格がないときは、税抜価格だけを出す', () => {
    expect(formatPrice(null, 1800)).toBe('税抜 1,800 円（税込価格は計算できません）')
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
