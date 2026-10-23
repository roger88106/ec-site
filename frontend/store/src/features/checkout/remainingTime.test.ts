import { describe, expect, test } from 'vitest'
import { remainingTime } from '@/features/checkout/remainingTime'

describe('引当の残り時間', () => {
  /** 期限 */
  const expiresAt = '2026-10-10T05:15:00Z'

  test('期限までの残りを「分:秒」で出す（秒の端数は切り捨て）', () => {
    expect(remainingTime(expiresAt, new Date('2026-10-10T05:00:00Z'))).toBe('15:00')
    expect(remainingTime(expiresAt, new Date('2026-10-10T05:14:51.500Z'))).toBe('0:08')
  })

  test('AC-84 15 分たって期限を過ぎたら null になる', () => {
    expect(remainingTime(expiresAt, new Date('2026-10-10T05:15:00Z'))).toBeNull()
    expect(remainingTime(expiresAt, new Date('2026-10-10T06:00:00Z'))).toBeNull()
  })
})
