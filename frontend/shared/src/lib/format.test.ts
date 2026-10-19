import { describe, expect, test } from 'vitest'
import { axisValuesLabel, formatDateTime, formatPostalCode, formatPrice, formatYen, taxLabel } from './format'

describe('共有の整形', () => {
  test('金額は 3 桁ごとに区切って「円」を付ける', () => {
    expect(formatYen(1980)).toBe('1,980 円')
  })

  test('価格は税込を主に、税抜を括弧で併記する。税込がなければ税抜だけ', () => {
    expect(formatPrice({ inclTax: 1980, exclTax: 1800 })).toBe('1,980 円（税抜 1,800 円）')
    expect(formatPrice({ inclTax: null, exclTax: 1800 })).toBe('税抜 1,800 円（税込価格は計算できません）')
  })

  test('日時は日本時間の「yyyy/MM/dd HH:mm」。空白は空文字', () => {
    expect(formatDateTime('2027-03-31T15:00:00Z')).toBe('2027/04/01 00:00')
    expect(formatDateTime(null)).toBe('')
  })

  test('税率ごとの消費税額は、対象額と消費税額を並べる', () => {
    expect(taxLabel({ ratePercent: 10, taxableAmount: 11000, taxAmount: 1000 })).toBe(
      '10% 対象 11,000 円（内消費税 1,000 円）',
    )
  })

  test('郵便番号は 3 桁と 4 桁をハイフンでつなぐ', () => {
    expect(formatPostalCode('1000001')).toBe('100-0001')
  })

  test('軸の名前と値を、渡した区切りで並べる', () => {
    const axes = [
      { name: '色', value: '黒' },
      { name: 'サイズ', value: 'M' },
    ]
    expect(axisValuesLabel(axes, '　')).toBe('色：黒　サイズ：M')
    expect(axisValuesLabel(axes, '、')).toBe('色：黒、サイズ：M')
  })
})
