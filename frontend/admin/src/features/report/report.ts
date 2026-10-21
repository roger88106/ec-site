import { LOCALE } from '@ec-site/shared/lib/locale'
import { translate } from '@/i18n/translate'

/** グラフの軸の金額の書き方（万・億で短くする） */
const COMPACT = new Intl.NumberFormat(LOCALE, { notation: 'compact', maximumFractionDigits: 1 })

/** グラフの軸の金額。例：「4000万円」「341.8万円」「5000円」 */
export function formatYenCompact(amount: number): string {
  return translate('report.yenCompact', { amount: COMPACT.format(amount) })
}
