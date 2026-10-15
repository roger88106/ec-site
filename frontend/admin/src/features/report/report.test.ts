import { describe, expect, it } from 'vitest'
import { ageGroupSalesAgeGroupValues, genderSalesGenderValues } from '@/api/generated/admin'
import {
  ageGroupLabel,
  granularityLabel,
  prefectureLabel,
  reportBasisLabel,
  reportGenderLabel,
} from '@/features/report/report'

describe('レポートの表示名', () => {
  it('BR-24 集計の粒度', () => {
    expect(granularityLabel('WEEK')).toBe('週')
    expect(granularityLabel('MONTH')).toBe('月')
    expect(granularityLabel('QUARTER')).toBe('四半期')
    expect(granularityLabel('YEAR')).toBe('年')
  })

  it('BR-25 売上の数え方', () => {
    expect(reportBasisLabel('ORDER')).toBe('注文基準')
    expect(reportBasisLabel('SHIPMENT')).toBe('出荷基準')
  })

  it('BR-27 年齢層は、すべての区分に表示名がある', () => {
    expect(ageGroupSalesAgeGroupValues.map(ageGroupLabel)).toEqual([
      '10 代以下',
      '20 代',
      '30 代',
      '40 代',
      '50 代',
      '60 代以上',
      '不明',
    ])
  })

  it('BR-29 性別は、すべての区分に表示名がある', () => {
    expect(genderSalesGenderValues.map(reportGenderLabel)).toEqual(['男性', '女性', 'その他', '不明'])
  })

  it('AC-220 都道府県は、全国地方公共団体コードから名前にする。コードがない・知らないコードは「不明」', () => {
    expect(prefectureLabel('01')).toBe('北海道')
    expect(prefectureLabel('13')).toBe('東京都')
    expect(prefectureLabel('47')).toBe('沖縄県')
    expect(prefectureLabel(undefined)).toBe('不明')
    expect(prefectureLabel('48')).toBe('不明')
  })
})
