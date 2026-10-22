import { describe, expect, test } from 'vitest'
import { normalizePostalCode } from '@/features/checkout/postalCode'

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
    expect(normalizePostalCode('-1234567')).toBe('1234567')
  })
})
