import { describe, expect, test } from 'vitest'
import { normalizePostalCode } from '@/lib/postalCode'

describe('郵便番号の正規化', () => {
  test('DC-135-6 ハイフンのない 7 桁の数字は、そのまま返す', () => {
    expect(normalizePostalCode('1234567')).toBe('1234567')
  })

  test('DC-135-6 ハイフンが 1 つ入った 7 桁の数字は、ハイフンを除いて返す', () => {
    expect(normalizePostalCode('123-4567')).toBe('1234567')
  })

  test('DC-135-6 桁が足りない、多い、空のときは null を返す', () => {
    expect(normalizePostalCode('123456')).toBeNull()
    expect(normalizePostalCode('12345678')).toBeNull()
    expect(normalizePostalCode('')).toBeNull()
  })

  test('DC-135-6 数字以外が入ると null を返す', () => {
    expect(normalizePostalCode('123456a')).toBeNull()
    expect(normalizePostalCode('123 4567')).toBeNull()
  })

  test('DC-135-7 除くのは最初のハイフン 1 つだけで、2 つ以上あると null を返す', () => {
    expect(normalizePostalCode('123-45-67')).toBeNull()
    expect(normalizePostalCode('123--4567')).toBeNull()
  })

  test('DC-135-7 先頭のハイフン 1 つも除かれる今の振る舞いを保つ', () => {
    expect(normalizePostalCode('-1234567')).toBe('1234567')
  })

  test('AC-416-1 全角の数字とハイフンの類も、変換して 7 桁にする', () => {
    expect(normalizePostalCode('１００－０００１')).toBe('1000001')
    expect(normalizePostalCode('１０００００１')).toBe('1000001')
    // U+30FC（長音符）、U+2212（マイナス記号）、U+2015（水平線）
    expect(normalizePostalCode('100\u30FC0001')).toBe('1000001')
    expect(normalizePostalCode('100\u22120001')).toBe('1000001')
    expect(normalizePostalCode('100\u20150001')).toBe('1000001')
  })

  test('AC-416-4 変換の後もハイフンが 2 つ以上ある全角の入力は、null を返す', () => {
    expect(normalizePostalCode('１００－００－０１')).toBeNull()
    expect(normalizePostalCode('１００－－０００１')).toBeNull()
  })
})
