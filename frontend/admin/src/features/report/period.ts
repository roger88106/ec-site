import { isDateString } from '@/lib/date'
import { toZonedDate } from '@/lib/format'

/** 期間の選択肢 */
export type PeriodPreset = 'thisFiscalYear' | 'lastFiscalYear' | 'thisMonth' | 'lastMonth' | 'thisYear' | 'lastYear'

/** 売上の推移の期間の選択肢（会計年度） */
export const FISCAL_PRESETS = ['thisFiscalYear', 'lastFiscalYear'] as const satisfies PeriodPreset[]

/** バリアントの売れ行き、地域・年齢層・性別の期間の選択肢（暦） */
export const CALENDAR_PRESETS = ['thisMonth', 'lastMonth', 'thisYear', 'lastYear'] as const satisfies PeriodPreset[]

/** 期間（日本時間の日付。両端を含む） */
export type Period = { from: string; to: string }

/** 実在する日付（yyyy-MM-dd）か */
export function isDate(value: string): boolean {
  if (!isDateString(value)) {
    return false
  }
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

/** いまの日本時間の日付（yyyy-MM-dd） */
export function todayInJapan(now: Date = new Date()): string {
  return toZonedDate(now)
}

/**
 * 選択肢の期間を求める。年度は会計年度の始まりの月から、月と年は暦で求める。
 *
 * @param preset 選択肢
 * @param today 今日（日本時間の日付）
 * @param fiscalYearStartMonth 会計年度の始まりの月（1〜12。レポートの設定）
 */
export function presetPeriod(preset: PeriodPreset, today: string, fiscalYearStartMonth: number): Period {
  const [year, month] = today.split('-').map(Number)
  switch (preset) {
    case 'thisFiscalYear':
    case 'lastFiscalYear': {
      // 今日の月が始まりの月より前なら、前の年に始まった年度
      const startYear = (month >= fiscalYearStartMonth ? year : year - 1) - (preset === 'lastFiscalYear' ? 1 : 0)
      return { from: firstDay(startYear, fiscalYearStartMonth), to: lastDay(startYear + 1, fiscalYearStartMonth - 1) }
    }
    case 'thisMonth':
      return { from: firstDay(year, month), to: lastDay(year, month) }
    case 'lastMonth':
      return { from: firstDay(year, month - 1), to: lastDay(year, month - 1) }
    case 'thisYear':
      return { from: firstDay(year, 1), to: lastDay(year, 12) }
    case 'lastYear':
      return { from: firstDay(year - 1, 1), to: lastDay(year - 1, 12) }
  }
}

/** 月の初日。月は 0 や 13 でもよい（前後の年に繰る） */
function firstDay(year: number, month: number): string {
  return format(new Date(Date.UTC(year, month - 1, 1)))
}

/** 月の末日。月は 0 や 13 でもよい（前後の年に繰る） */
function lastDay(year: number, month: number): string {
  return format(new Date(Date.UTC(year, month, 0)))
}

/** UTC の日付を yyyy-MM-dd にする */
function format(date: Date): string {
  return date.toISOString().slice(0, 10)
}
