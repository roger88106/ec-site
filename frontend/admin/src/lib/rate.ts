import { LOCALE } from '@ec-site/shared/lib/locale'
import { translate } from '@/i18n/translate'

/** 率の書式（小数は 2 桁まで） */
const percent = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 2 })

/** 税率の表示。例：「10%」「8.5%」。null は「なし」 */
export function formatRate(ratePercent: number | null | undefined): string {
  return ratePercent == null
    ? translate('format.rate.none') // なし
    : translate('format.rate.value', { rate: percent.format(ratePercent) }) // 率に「%」を付ける
}
