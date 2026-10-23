import { describe, expect, test } from 'vitest'
import { parseCondition, type ReportCondition, toSearchParams } from '@/features/report/reportCondition'

/** URL に条件がないときの期間 */
const DEFAULT_PERIOD = { from: '2026-04-01', to: '2027-03-31' }

/** 商品 ID */
const PRODUCT_ID = '00000000-0000-0000-0000-000000002001'

describe('レポートの条件と URL の検索パラメータ', () => {
  test('URL に条件がなければ、既定の期間、月、注文基準、全商品、消費税・送料を含む', () => {
    expect(parseCondition(new URLSearchParams(), DEFAULT_PERIOD)).toEqual({
      from: '2026-04-01',
      to: '2027-03-31',
      granularity: 'MONTH',
      basis: 'ORDER',
      productId: undefined,
      excludeTax: false,
      excludeShipping: false,
    })
  })

  test('URL の条件を読む', () => {
    const params = new URLSearchParams(
      `from=2025-01-01&to=2025-12-31&granularity=QUARTER&basis=SHIPMENT&productId=${PRODUCT_ID}&excludeTax=true&excludeShipping=true`,
    )

    expect(parseCondition(params, DEFAULT_PERIOD)).toEqual({
      from: '2025-01-01',
      to: '2025-12-31',
      granularity: 'QUARTER',
      basis: 'SHIPMENT',
      productId: PRODUCT_ID,
      excludeTax: true,
      excludeShipping: true,
    })
  })

  test('正しくない値は無視して既定にする', () => {
    const params = new URLSearchParams(
      'from=2025-02-30&to=yesterday&granularity=DAY&basis=PAID&productId=abc&excludeTax=yes&excludeShipping=1',
    )

    expect(parseCondition(params, DEFAULT_PERIOD)).toEqual({
      from: '2026-04-01',
      to: '2027-03-31',
      granularity: 'MONTH',
      basis: 'ORDER',
      productId: undefined,
      excludeTax: false,
      excludeShipping: false,
    })
  })

  test('URL には期間と、既定でない条件だけを書く', () => {
    const condition: ReportCondition = {
      from: '2026-04-01',
      to: '2027-03-31',
      granularity: 'MONTH',
      basis: 'ORDER',
      productId: undefined,
      excludeTax: false,
      excludeShipping: false,
    }

    expect(toSearchParams(condition).toString()).toBe('from=2026-04-01&to=2027-03-31')
    expect(
      toSearchParams({
        ...condition,
        granularity: 'WEEK',
        basis: 'SHIPMENT',
        productId: PRODUCT_ID,
        excludeTax: true,
        excludeShipping: true,
      }).toString(),
    ).toBe(
      `from=2026-04-01&to=2027-03-31&granularity=WEEK&basis=SHIPMENT&productId=${PRODUCT_ID}&excludeTax=true&excludeShipping=true`,
    )
  })

  test('URL に書いた条件を読むと、同じ条件に戻る', () => {
    const condition: ReportCondition = {
      from: '2025-10-01',
      to: '2025-10-31',
      granularity: 'YEAR',
      basis: 'SHIPMENT',
      productId: PRODUCT_ID,
      excludeTax: true,
      excludeShipping: false,
    }

    expect(parseCondition(toSearchParams(condition), DEFAULT_PERIOD)).toEqual(condition)
  })
})
