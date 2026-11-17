import { isUuid } from '@ec-site/shared/lib/patterns'
import { granularityValues, reportBasisValues } from '@/api/generated/admin'
import type { Granularity, ReportBasis } from '@/api/types'
import { isDate, type Period } from '@/features/report/period'
import { enumParam, writeParams } from '@/lib/searchParams'

/** 集計の粒度の既定（月） */
export const DEFAULT_GRANULARITY: Granularity = 'MONTH'

/** 売上の数え方の既定（注文基準） */
export const DEFAULT_BASIS: ReportBasis = 'ORDER'

/**
 * レポートの条件
 *
 * URL のクエリに持つ（再読込とリンクの共有のため。管理画面の構成の「画面の URL」）。
 */
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

/**
 * URL のクエリから条件を読む。
 *
 * 不正な値は無視し、期間は与えた既定にする。
 *
 * @param params        URL のクエリ
 * @param defaultPeriod 既定の期間
 * @returns レポートの条件
 */
export function parseCondition(params: URLSearchParams, defaultPeriod: Period): ReportCondition {
  const granularity = params.get('granularity')
  const basis = params.get('basis')
  const productId = params.get('productId')
  const from = params.get('from')
  const to = params.get('to')
  return {
    from: from !== null && isDate(from) ? from : defaultPeriod.from,
    to: to !== null && isDate(to) ? to : defaultPeriod.to,
    granularity: enumParam(granularityValues, granularity, DEFAULT_GRANULARITY),
    basis: enumParam(reportBasisValues, basis, DEFAULT_BASIS),
    productId: productId !== null && isUuid(productId) ? productId : undefined,
    excludeTax: params.get('excludeTax') === 'true',
    excludeShipping: params.get('excludeShipping') === 'true',
  }
}

/**
 * 条件を、どのレポートの API にも共通の query にする。
 *
 * 粒度・商品・送料は、レポートごとに使い方が違うので、呼び出す側で追加する。
 *
 * @param condition レポートの条件
 * @returns 期間・数え方・消費税の query
 */
export function toReportQuery(condition: ReportCondition) {
  return {
    from: condition.from,
    to: condition.to,
    basis: condition.basis,
    excludeTax: condition.excludeTax,
  }
}

/**
 * 条件を URL のクエリにする。
 *
 * 期間は必ず書き、既定の条件（月、注文基準、全商品、含む）は書かない。
 *
 * @param condition レポートの条件
 * @returns URL のクエリ
 */
export function toSearchParams(condition: ReportCondition): URLSearchParams {
  const { from, to, ...rest } = condition
  // 期間は空でも書く（他の項目は空と既定を書かない）
  const params = new URLSearchParams({ from, to })
  for (const [name, value] of writeParams(rest, { granularity: DEFAULT_GRANULARITY, basis: DEFAULT_BASIS })) {
    params.set(name, value)
  }
  return params
}
