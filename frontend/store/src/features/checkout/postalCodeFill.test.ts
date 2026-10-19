import { describe, expect, test } from 'vitest'
import type { PostalCodeAddress } from '@/api/types'
import { postalCodeFill } from '@/features/checkout/postalCodeFill'

/** 郵便番号の住所 */
function address(prefectureCode: string, municipalityCode: string, townName: string): PostalCodeAddress {
  return {
    prefecture: { code: prefectureCode, name: `都道府県${prefectureCode}` },
    municipality: { code: municipalityCode, name: `市区町村${municipalityCode}` },
    townName,
  }
}

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
    expect(postalCodeFill([address('06', '06205', '甲'), address('06', '06205', '乙')], '')).toEqual({
      prefectureCode: '06',
      municipalityCode: '06205',
      address: null,
    })
  })

  test('複数で市区町村が違えば、何も補わない', () => {
    expect(postalCodeFill([address('06', '06205', '甲'), address('06', '06361', '乙')], '')).toBeNull()
  })

  test('AC-256 結果がなければ、何も補わない', () => {
    expect(postalCodeFill([], '')).toBeNull()
  })
})
