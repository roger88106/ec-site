import { describe, expect, test } from 'vitest'
import { formatPrice } from '@/lib/price'

describe('価格の表示（BR-4）', () => {
  test('BR-4 税込価格を主に、税抜価格を括弧で併記する', () => {
    expect(formatPrice({ inclTax: 1980, exclTax: 1800 })).toBe('1,980 円（税抜 1,800 円）')
  })

  test('BR-4 3 桁未満と 7 桁以上も、3 桁ごとに区切る', () => {
    expect(formatPrice({ inclTax: 162, exclTax: 150 })).toBe('162 円（税抜 150 円）')
    expect(formatPrice({ inclTax: 1100000, exclTax: 1000000 })).toBe('1,100,000 円（税抜 1,000,000 円）')
  })
})
