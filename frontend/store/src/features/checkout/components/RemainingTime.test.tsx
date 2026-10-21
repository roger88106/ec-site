// @vitest-environment jsdom
import { act, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { RemainingTime } from '@/features/checkout/components/RemainingTime'
import { renderInBrowser } from '@/test/render'

/** テストの現在時刻 */
const NOW = new Date('2026-10-16T10:00:00Z')

describe('引当の残り時間', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('残り時間を出し、1 秒ごとに数え直して、期限を過ぎると案内を替える', () => {
    renderInBrowser(<RemainingTime expiresAt="2026-10-16T10:00:02Z" />)

    expect(screen.getByText('0:02')).toBeTruthy()

    act(() => vi.advanceTimersByTime(1000))
    expect(screen.getByText('0:01')).toBeTruthy()

    act(() => vi.advanceTimersByTime(1000))
    expect(screen.getByText(/在庫の確保の期限が切れました。/)).toBeTruthy()
  })
})
