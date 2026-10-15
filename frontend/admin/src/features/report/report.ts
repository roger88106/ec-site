import { LOCALE } from '@ec-site/shared/lib/locale'
import type { AgeGroup, Granularity, ReportBasis, ReportGender, SalesTrendPoint } from '@/api/types'
import { messages } from '@/i18n/messages'
import { translate } from '@/i18n/translate'

/** 集計の粒度の表示名 */
export function granularityLabel(granularity: Granularity): string {
  return translate(`report.granularity.${granularity}`)
}

/** 売上の数え方の表示名 */
export function reportBasisLabel(basis: ReportBasis): string {
  return translate(`report.basis.${basis}`)
}

/** 年齢層の表示名 */
export function ageGroupLabel(ageGroup: AgeGroup): string {
  return translate(`report.ageGroup.${ageGroup}`)
}

/** レポートの性別の区分の表示名 */
export function reportGenderLabel(gender: ReportGender): string {
  return translate(`report.gender.${gender}`)
}

/** 都道府県の表示名。コード（全国地方公共団体コードの 2 桁）がなければ、または知らないコードなら「不明」 */
export function prefectureLabel(code: string | undefined): string {
  if (code === undefined || !(code in messages.report.prefecture)) {
    return translate('report.unknownPrefecture')
  }
  return translate(`report.prefecture.${code as keyof typeof messages.report.prefecture}`)
}

/**
 * 売上の推移の区切りの表示名。週は期間で切った始まりと終わりの日、月は暦の年と月、四半期と年は会計年度で呼ぶ。<br />
 * 例：「2026/04/06〜2026/04/12」「2026 年 4 月」「2026 年度 第 1 四半期」「2026 年度」
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

/** グラフの軸の金額の書き方（万・億で短くする） */
const COMPACT = new Intl.NumberFormat(LOCALE, { notation: 'compact', maximumFractionDigits: 1 })

/** グラフの軸の金額。例：「4000万円」「341.8万円」「5000円」 */
export function formatYenCompact(amount: number): string {
  return `${COMPACT.format(amount)}円`
}
