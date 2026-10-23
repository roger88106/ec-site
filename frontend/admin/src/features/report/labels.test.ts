import { describe, expect, it } from 'vitest'
import { ageGroupSalesAgeGroupValues, genderSalesGenderValues } from '@/api/generated/admin'
import {
  ageGroupLabel,
  bucketLabel,
  granularityLabel,
  prefectureLabel,
  reportBasisLabel,
  reportGenderLabel,
  variantSalesLabel,
} from '@/features/report/labels'

describe('レポートの表示名', () => {
  it('AC-197 集計の粒度は、週・月・四半期・年と表示する', () => {
    expect(granularityLabel('WEEK')).toBe('週')
    expect(granularityLabel('MONTH')).toBe('月')
    expect(granularityLabel('QUARTER')).toBe('四半期')
    expect(granularityLabel('YEAR')).toBe('年')
  })

  it('AC-200 売上の数え方は、注文基準・出荷基準と表示する', () => {
    expect(reportBasisLabel('ORDER')).toBe('注文基準')
    expect(reportBasisLabel('SHIPMENT')).toBe('出荷基準')
  })

  it('AC-206 年齢層は、すべての区分に表示名がある', () => {
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

  it('AC-219 性別は、すべての区分に表示名がある', () => {
    expect(genderSalesGenderValues.map(reportGenderLabel)).toEqual(['男性', '女性', 'その他', '不明'])
  })

  it('AC-220、262 都道府県は、応答の名前をそのまま出す。名前がなければ「不明」', () => {
    expect(prefectureLabel('東京都')).toBe('東京都')
    expect(prefectureLabel(undefined)).toBe('不明')
  })

  it('AC-197 売上の推移の区切りは、週は日付、月は暦、四半期と年は会計年度で呼ぶ', () => {
    const point = { start: '2026-04-06', end: '2026-04-12', amount: 0, quantity: 0 }
    expect(bucketLabel(point, 'WEEK')).toBe('2026/04/06〜2026/04/12')
    expect(bucketLabel({ ...point, start: '2026-04-01', end: '2026-04-30' }, 'MONTH')).toBe('2026 年 4 月')
    expect(
      bucketLabel({ ...point, start: '2027-01-01', end: '2027-03-31', fiscalYear: 2026, quarter: 4 }, 'QUARTER'),
    ).toBe('2026 年度 第 4 四半期')
    expect(bucketLabel({ ...point, start: '2026-04-01', end: '2027-03-31', fiscalYear: 2026 }, 'YEAR')).toBe(
      '2026 年度',
    )
  })

  it('AC-202 バリアントの売れ行きの表示名は、軸があれば「名前：値」を「、」で並べ、軸がなければ品番にする', () => {
    const variant = { variantId: 'v1', variantCode: 'TEE-001-WH-M', quantity: 0, amount: 0 }
    expect(
      variantSalesLabel({
        ...variant,
        axisValues: [
          { name: '色', value: '白' },
          { name: 'サイズ', value: 'M' },
        ],
      }),
    ).toBe('色：白、サイズ：M')
    expect(variantSalesLabel({ ...variant, axisValues: [{ name: '色', value: '白' }] })).toBe('色：白')
    expect(variantSalesLabel({ ...variant, axisValues: [] })).toBe('TEE-001-WH-M')
  })
})
