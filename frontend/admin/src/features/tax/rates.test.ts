import { describe, expect, test } from 'vitest'
import { rateState } from '@/features/tax/rates'

const NOW = Date.parse('2026-10-08T00:00:00+09:00')

describe('税率の行の状態', () => {
  test('新しい行は「新規」', () => {
    expect(rateState({ rateId: null, effectiveFrom: null, effectiveTo: null }, NOW)).toBe('新規')
  })

  test('AC-5 終了日時が現在以前なら「終了済み」（終了日時ちょうどは含まない）', () => {
    expect(rateState({ rateId: 'a', effectiveFrom: null, effectiveTo: '2026-10-08T00:00:00+09:00' }, NOW)).toBe(
      '終了済み',
    )
  })

  test('AC-5 開始が空白か現在以前なら「適用中」', () => {
    expect(rateState({ rateId: 'a', effectiveFrom: null, effectiveTo: null }, NOW)).toBe('適用中')
    expect(rateState({ rateId: 'a', effectiveFrom: '2026-10-08T00:00:00+09:00', effectiveTo: null }, NOW)).toBe(
      '適用中',
    )
  })

  test('AC-5 開始が現在より後なら「未開始」', () => {
    expect(rateState({ rateId: 'a', effectiveFrom: '2027-04-01T00:00:00+09:00', effectiveTo: null }, NOW)).toBe(
      '未開始',
    )
  })
})
