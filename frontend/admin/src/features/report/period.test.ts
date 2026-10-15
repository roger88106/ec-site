import { describe, expect, it } from 'vitest'
import { CALENDAR_PRESETS, FISCAL_PRESETS, presetPeriod, todayInJapan } from '@/features/report/period'

describe('期間の選択肢', () => {
  it('BR-24 今年度と前年度は、会計年度（始まりの月が 4）で求める', () => {
    expect(presetPeriod('thisFiscalYear', '2026-10-15', 4)).toEqual({ from: '2026-04-01', to: '2027-03-31' })
    expect(presetPeriod('lastFiscalYear', '2026-10-15', 4)).toEqual({ from: '2025-04-01', to: '2026-03-31' })
  })

  it('BR-24 始まりの月より前の月は、前の年に始まった年度に入る', () => {
    expect(presetPeriod('thisFiscalYear', '2027-02-10', 4)).toEqual({ from: '2026-04-01', to: '2027-03-31' })
    expect(presetPeriod('thisFiscalYear', '2026-04-01', 4)).toEqual({ from: '2026-04-01', to: '2027-03-31' })
    expect(presetPeriod('thisFiscalYear', '2026-03-31', 4)).toEqual({ from: '2025-04-01', to: '2026-03-31' })
  })

  it('BR-24 始まりの月が 1 なら、会計年度は暦の年と同じ', () => {
    expect(presetPeriod('thisFiscalYear', '2026-10-15', 1)).toEqual({ from: '2026-01-01', to: '2026-12-31' })
    expect(presetPeriod('lastFiscalYear', '2026-10-15', 1)).toEqual({ from: '2025-01-01', to: '2025-12-31' })
  })

  it('BR-24 今月・先月・今年・去年は暦で求める（うるう年の 2 月の末日を含む）', () => {
    expect(presetPeriod('thisMonth', '2028-02-10', 4)).toEqual({ from: '2028-02-01', to: '2028-02-29' })
    expect(presetPeriod('lastMonth', '2026-01-15', 4)).toEqual({ from: '2025-12-01', to: '2025-12-31' })
    expect(presetPeriod('thisYear', '2026-10-15', 4)).toEqual({ from: '2026-01-01', to: '2026-12-31' })
    expect(presetPeriod('lastYear', '2026-10-15', 4)).toEqual({ from: '2025-01-01', to: '2025-12-31' })
  })

  it('BR-24 売上の推移は会計年度の、他のレポートは暦の選択肢を出す', () => {
    expect(FISCAL_PRESETS).toEqual(['thisFiscalYear', 'lastFiscalYear'])
    expect(CALENDAR_PRESETS).toEqual(['thisMonth', 'lastMonth', 'thisYear', 'lastYear'])
  })

  it('BR-24 今日の日付は日本時間で求める', () => {
    expect(todayInJapan(new Date('2026-09-30T15:00:00Z'))).toBe('2026-10-01')
    expect(todayInJapan(new Date('2026-09-30T14:59:59Z'))).toBe('2026-09-30')
  })
})
