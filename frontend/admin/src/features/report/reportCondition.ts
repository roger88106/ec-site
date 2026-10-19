import { isUuid } from '@ec-site/shared/lib/patterns'
import { granularityValues, reportBasisValues } from '@/api/generated/admin'
import type { Granularity, ReportBasis } from '@/api/types'
import { isDate } from '@/features/report/period'

/** レポートの条件。URL のクエリに持つ（再読込とリンクの共有のため。管理画面の構成 第 3 節） */
export type ReportCondition = {
  /** 期間（日本時間の日付。両端を含む） */
  from: string
  to: string
  /** 集計の粒度（売上の推移だけが使う） */
  granularity: Granularity
  /** 売上の数え方 */
  basis: ReportBasis
  /** 商品 ID。undefined は全商品 */
  productId: string | undefined
  /** 消費税を除くか */
  excludeTax: boolean
  /** 送料を除くか */
  excludeShipping: boolean
}

/** URL のクエリから条件を読む。不正な値は無視し、期間は与えた既定にする */
export function parseCondition(params: URLSearchParams, defaultPeriod: { from: string; to: string }): ReportCondition {
  const granularity = params.get('granularity')
  const basis = params.get('basis')
  const productId = params.get('productId')
  const from = params.get('from')
  const to = params.get('to')
  return {
    from: from !== null && isDate(from) ? from : defaultPeriod.from,
    to: to !== null && isDate(to) ? to : defaultPeriod.to,
    granularity: granularityValues.includes(granularity as Granularity) ? (granularity as Granularity) : 'MONTH',
    basis: reportBasisValues.includes(basis as ReportBasis) ? (basis as ReportBasis) : 'ORDER',
    productId: productId !== null && isUuid(productId) ? productId : undefined,
    excludeTax: params.get('excludeTax') === 'true',
    excludeShipping: params.get('excludeShipping') === 'true',
  }
}

/** 条件を URL のクエリにする。期間は必ず書き、既定の条件（月、注文基準、全商品、含む）は書かない */
export function toSearchParams(condition: ReportCondition): URLSearchParams {
  const params = new URLSearchParams({ from: condition.from, to: condition.to })
  if (condition.granularity !== 'MONTH') params.set('granularity', condition.granularity)
  if (condition.basis !== 'ORDER') params.set('basis', condition.basis)
  if (condition.productId) params.set('productId', condition.productId)
  if (condition.excludeTax) params.set('excludeTax', 'true')
  if (condition.excludeShipping) params.set('excludeShipping', 'true')
  return params
}
