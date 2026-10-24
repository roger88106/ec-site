import { describe, expect, test } from 'vitest'
import { postalCodeFill } from '@/features/checkout/postalCodeFill'
import { postalCodeAddress as address } from '@/test/fixtures'

describe('郵便番号で補うもの', () => {
  test('AC-255 1 件なら都道府県と市区町村を選び、住所の欄が空なら町域を入れる', () => {
    expect(postalCodeFill([address('13', '13101', '千代田')], '')).toEqual({
      prefectureCode: '13',
      municipalityCode: '13101',
      address: '千代田',
    })
  })

  test('AC-255 住所の欄に入力があれば（空白だけを除く）、町域を入れない', () => {
    expect(postalCodeFill([address('13', '13101', '千代田')], '丸の内 1-1')).toEqual({
      prefectureCode: '13',
      municipalityCode: '13101',
      address: null,
    })
    expect(postalCodeFill([address('13', '13101', '千代田')], '  ')).toMatchObject({ address: '千代田' })
  })

  test('AC-283 町域が空（注記だけの町域）なら、住所の欄を変えない', () => {
    expect(postalCodeFill([address('01', '01101', '')], '')).toMatchObject({ address: null })
  })

  test('複数で都道府県と市区町村がすべて同じなら、それを選び、町域は入れない', () => {
    expect(postalCodeFill([address('13', '13101', '甲'), address('13', '13101', '乙')], '')).toEqual({
      prefectureCode: '13',
      municipalityCode: '13101',
      address: null,
    })
  })

  test('複数で市区町村が違えば、何も補わない', () => {
    expect(postalCodeFill([address('13', '13101', '甲'), address('13', '13102', '乙')], '')).toBeNull()
  })

  test('AC-256 結果がなければ、何も補わない', () => {
    expect(postalCodeFill([], '')).toBeNull()
  })
})
