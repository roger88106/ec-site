import type { AgeGroup, Granularity, ReportBasis, ReportGender } from '@/api/types'
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
