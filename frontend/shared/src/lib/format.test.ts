import { describe, expect, test } from 'vitest'
import {
  axisValuesLabel,
  formatDateTime,
  formatNumber,
  formatPostalCode,
  formatPrice,
  formatYen,
  japanDateTimeFormat,
  taxLabel,
} from './format'

describe('共有の整形', () => {
  test('金額は 3 桁ごとに区切って「円」を付ける', () => {
    expect(formatYen(1980)).toBe('1,980 円')
  })

  test('数は 3 桁ごとに区切る。単位は付けない', () => {
    expect(formatNumber(1234)).toBe('1,234')
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatNumber(0)).toBe('0')
  })

  test('価格は税込を主に、税抜を括弧で併記する。税込がなければ税抜だけ', () => {
    expect(formatPrice({ inclTax: 1980, exclTax: 1800 })).toBe('1,980 円（税抜 1,800 円）')
    expect(formatPrice({ inclTax: null, exclTax: 1800 })).toBe('税抜 1,800 円（税込価格は計算できません）')
  })

  test('日時は日本時間の「yyyy/MM/dd HH:mm」。空白は空文字', () => {
    expect(formatDateTime('2027-03-31T15:00:00Z')).toBe('2027/04/01 00:00')
    expect(formatDateTime(null)).toBe('')
  })

  test('日本時間の書式は、年・月・日・時・分をゼロ埋めの部品で取り出せる', () => {
    const parts = Object.fromEntries(
      japanDateTimeFormat.formatToParts(new Date('2027-03-31T15:05:00Z')).map((part) => [part.type, part.value]),
    )
    expect(parts).toMatchObject({ year: '2027', month: '04', day: '01', hour: '00', minute: '05' })
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

describe('金額の表示（BR-7、8）', () => {
  test('金額は 3 桁ごとに区切って「円」を付ける', () => {
    expect(formatYen(1000)).toBe('1,000 円')
    expect(formatYen(0)).toBe('0 円')
  })

  test('BR-8 税率ごとの消費税額は、対象額と消費税額を並べる', () => {
    expect(taxLabel({ ratePercent: 10, taxableAmount: 11000, taxAmount: 1000 })).toBe(
      '10% 対象 11,000 円（内消費税 1,000 円）',
    )
    expect(taxLabel({ ratePercent: 8, taxableAmount: 1080, taxAmount: 80 })).toBe('8% 対象 1,080 円（内消費税 80 円）')
  })
})

describe('日時・郵便番号の表示', () => {
  test('日時は日本時間で「年/月/日 時:分」', () => {
    expect(formatDateTime('2026-10-10T15:05:00Z')).toBe('2026/10/11 00:05')
  })

  test('郵便番号は 3 桁と 4 桁の間にハイフンを入れる', () => {
    expect(formatPostalCode('1000001')).toBe('100-0001')
  })
})

describe('価格の表示（BR-4）', () => {
  test('BR-4 税込価格を主に、税抜価格を括弧で併記する', () => {
    expect(formatPrice({ inclTax: 1980, exclTax: 1800 })).toBe('1,980 円（税抜 1,800 円）')
  })

  test('BR-4 3 桁未満と 7 桁以上も、3 桁ごとに区切る', () => {
    expect(formatPrice({ inclTax: 162, exclTax: 150 })).toBe('162 円（税抜 150 円）')
    expect(formatPrice({ inclTax: 1100000, exclTax: 1000000 })).toBe('1,100,000 円（税抜 1,000,000 円）')
  })
})

describe('日時の表示（BR-3）', () => {
  test('BR-3 日本時間で表示する', () => {
    expect(formatDateTime('2027-03-31T15:00:00Z')).toBe('2027/04/01 00:00')
  })

  test('BR-3 オフセットが日本時間以外でも、日本時間に直して表示する', () => {
    expect(formatDateTime('2027-04-01T09:30:00+09:00')).toBe('2027/04/01 09:30')
    expect(formatDateTime('2027-03-31T20:30:00-05:00')).toBe('2027/04/01 10:30')
  })

  test('空白（null）は空文字にする', () => {
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime(undefined)).toBe('')
  })
})

describe('税率ごとの消費税額（BR-8）', () => {
  test('BR-8 対象額と消費税額を並べる', () => {
    expect(taxLabel({ ratePercent: 10, taxableAmount: 11000, taxAmount: 1000 })).toBe(
      '10% 対象 11,000 円（内消費税 1,000 円）',
    )
  })
})

describe('お届け先の表示', () => {
  test('郵便番号は 3 桁と 4 桁の間にハイフンを入れる', () => {
    expect(formatPostalCode('1000001')).toBe('100-0001')
  })
})

describe('価格の表示（BR-4）', () => {
  test('BR-4 税込価格を主に、税抜価格を括弧で併記する', () => {
    expect(formatPrice({ inclTax: 1980, exclTax: 1800 })).toBe('1,980 円（税抜 1,800 円）')
  })

  test('BR-4 3 桁未満と 7 桁以上も、3 桁ごとに区切る', () => {
    expect(formatPrice({ inclTax: 110, exclTax: 100 })).toBe('110 円（税抜 100 円）')
    expect(formatPrice({ inclTax: 1100000, exclTax: 1000000 })).toBe('1,100,000 円（税抜 1,000,000 円）')
  })

  test('有効な税率がなく税込価格がないときは、税抜価格だけを出す', () => {
    expect(formatPrice({ inclTax: null, exclTax: 1800 })).toBe('税抜 1,800 円（税込価格は計算できません）')
  })
})
