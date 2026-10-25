// @vitest-environment jsdom
import { act } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { RemainingTime } from '@/features/checkout/components/RemainingTime'
import { renderInBrowser } from '@/test/render'

/** テストの現在時刻 */
const NOW = new Date('2026-10-16T10:00:00Z')

/** 画面に出ている文字の全て（探し方に左右されず、表示そのものを確認する） */
function shownText() {
  return document.body.textContent
}

describe('引当の残り時間', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
    vi.setSystemTime(NOW)
  })

  test('残り時間を出し、1 秒ごとに数え直して、期限を過ぎると案内を替える', () => {
    renderInBrowser(<RemainingTime expiresAt="2026-10-16T10:00:02Z" />)

    expect(shownText()).toBe('ご注文の商品の在庫を確保しています。残り 0:02')

    act(() => vi.advanceTimersByTime(1000))
    expect(shownText()).toBe('ご注文の商品の在庫を確保しています。残り 0:01')

    act(() => vi.advanceTimersByTime(1000))
    expect(shownText()).toContain('在庫の確保の期限が切れました。')
    expect(shownText()).not.toContain('残り')
  })
})
