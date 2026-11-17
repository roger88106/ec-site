import { act } from '@testing-library/react'
import { vi } from 'vitest'

/** 1 分（ミリ秒） */
export const MINUTE = 60 * 1000

/**
 * 無操作のテストで時計を始める時刻
 *
 * 日本時間の 2026/01/01 09:00:00。
 */
export const START = Date.UTC(2026, 0, 1)

/**
 * 偽の時計を START から始める。
 *
 * 例：`beforeEach(() => startClock())`
 *
 * 偽の時計は、テストの後に setup.ts が戻す。
 *
 * @param options.shouldAdvanceTime 偽の時計を実際の時間とともにも進めるか（user-event で操作するテスト）
 */
export function startClock(options: { shouldAdvanceTime?: boolean } = {}): void {
  vi.useFakeTimers(options)
  vi.setSystemTime(START)
}

/**
 * 時計とタイマーを進める（タイマーが動く通常の経過）。
 *
 * 例：`advanceClock(28 * MINUTE)`
 *
 * @param ms 進めるミリ秒
 */
export function advanceClock(ms: number): void {
  act(() => {
    vi.advanceTimersByTime(ms)
  })
}

/**
 * 他のタブがローカルストレージに書いた・削除したことを、storage イベントで知らせる。
 *
 * 例：`otherTabWrites(LOGOUT_KEY, String(START))`
 *
 * @param key   キー
 * @param value 書いた値。null は削除
 */
export function otherTabWrites(key: string, value: string | null): void {
  act(() => {
    if (value === null) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, value)
    }
    // storage イベントは書いたタブには起きず、他のタブにだけ起きるので、他のタブの書き込みとして直接起こす
    window.dispatchEvent(new StorageEvent('storage', { key, newValue: value }))
  })
}

/**
 * window で操作のイベントを起こす。
 *
 * 例：`operate('keydown')`
 *
 * @param type イベントの種類
 */
export function operate(type: string): void {
  // 無操作の判定は window で捕捉の段階に受け、要素を問わないので、user-event でなく window に直接起こす
  act(() => {
    window.dispatchEvent(new Event(type))
  })
}

/**
 * タブに戻ったことを知らせるイベント
 *
 * pageshow は戻る・進むのキャッシュから戻った、visibilitychange は再び見えた、focus は前面に戻った。
 */
export type TabComeBack = 'pageshow' | 'visibilitychange' | 'focus'

/**
 * タブに戻ったことを、ブラウザーが起こすイベントで知らせる。
 *
 * 例：`comeBackToTab('pageshow')`
 *
 * @param type イベントの種類
 */
export function comeBackToTab(type: TabComeBack): void {
  // ブラウザーが起こすイベントで、user-event では起こせないので、直接起こす
  act(() => {
    if (type === 'pageshow') {
      window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
    } else if (type === 'visibilitychange') {
      document.dispatchEvent(new Event('visibilitychange'))
    } else {
      window.dispatchEvent(new Event('focus'))
    }
  })
}
