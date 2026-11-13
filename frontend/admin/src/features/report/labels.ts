import { axisValuesLabel } from '@ec-site/shared/lib/format'
import type { AgeGroup, Granularity, ReportBasis, ReportGender, SalesTrendPoint, VariantSales } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 集計の粒度の表示名を返す。 */
export function granularityLabel(granularity: Granularity): string {
  return translate(`report.granularity.${granularity}`)
}

/** 売上の数え方の表示名を返す。 */
export function reportBasisLabel(basis: ReportBasis): string {
  return translate(`report.basis.${basis}`)
}

/** 年齢層の表示名を返す。 */
export function ageGroupLabel(ageGroup: AgeGroup): string {
  return translate(`report.ageGroup.${ageGroup}`)
}

/** レポートの性別の区分の表示名を返す。 */
export function reportGenderLabel(gender: ReportGender): string {
  return translate(`report.gender.${gender}`)
}

/**
 * 都道府県の表示名を返す。
 *
 * 応答の名前（地域のマスターデータの名前）をそのまま出し、なければ「不明」にする。
 *
 * @param name 応答の都道府県の名前
 * @returns 表示名
 */
export function prefectureLabel(name: string | undefined): string {
  return name ?? translate('report.unknownPrefecture') // 不明
}

/**
 * 売上の推移の区切りの表示名を作る。
 *
 * 週は期間で切った始まりと終わりの日、月は暦の年と月、四半期と年は会計年度で呼ぶ。
 * 例：「2026/04/06〜2026/04/12」「2026 年 4 月」「2026 年度 第 1 四半期」「2026 年度」
 *
 * @param point       推移の 1 点
 * @param granularity 集計の粒度
 * @returns 区切りの表示名
 */
export function bucketLabel(point: SalesTrendPoint, granularity: Granularity): string {
  const [year, month] = point.start.split('-').map(Number)
  return translate(`report.bucket.${granularity}`, {
    start: point.start.replaceAll('-', '/'),
    end: point.end.replaceAll('-', '/'),
    year,
    month,
    fiscalYear: point.fiscalYear ?? year,
    quarter: point.quarter ?? 0,
  })
}

/**
 * バリアントの売れ行きの表示名を返す。
 *
 * 軸があれば「名前：値」を区切りの文言で並べ（例：「色：白、サイズ：M」）、軸がなければ品番にする。
 *
 * @param variant バリアントの売れ行き
 * @returns 表示名
 */
export function variantSalesLabel(variant: VariantSales): string {
  return variant.axisValues.length > 0
    ? axisValuesLabel(variant.axisValues, translate('app.common.listSeparator')) // 読点
    : variant.variantCode
}
