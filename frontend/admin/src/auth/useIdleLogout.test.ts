import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import { IDLE_CHECK_INTERVAL_MS, IDLE_LIMIT_MS, LAST_ACTIVITY_KEY } from '@/auth/idle'
import { useIdleLogout } from '@/auth/useIdleLogout'
import { advanceClock, MINUTE, operate, otherTabWrites, START, startClock } from '@/test/idle'

/** 1 日（ミリ秒） */
const DAY = 24 * 60 * MINUTE

/** タイマーを進めずに時刻だけ進める（PC のスリープ。偽の時計は、登録したタイマーの実行時刻も同じだけ動かす） */
function sleep(ms: number) {
  act(() => {
    vi.setSystemTime(Date.now() + ms)
  })
}

/** タイマーを進めずに時刻だけ進め、タブが再び見えた・前面に戻ったことを知らせる（PC のスリープからの再開） */
function sleepAndWake(ms: number, wake: 'visibilitychange' | 'focus' | 'pageshow' = 'visibilitychange') {
  act(() => {
    vi.setSystemTime(Date.now() + ms)
    const target = wake === 'visibilitychange' ? document : window
    target.dispatchEvent(new Event(wake))
  })
}

/** 他のタブが最後に操作した時刻を書いたことを、storage イベントで知らせる */
function otherTabOperatesAt(at: number) {
  otherTabWrites(LAST_ACTIVITY_KEY, String(at))
}

/** addEventListener・removeEventListener の呼び出しから、イベントの種類とリスナーの組だけを取り出す（順は問わない） */
function listeners(calls: unknown[][]) {
  return new Set(calls.map(([type, listener]) => [type, listener]))
}

function renderIdle() {
  const onExpire = vi.fn()
  const view = renderHook(() => useIdleLogout({ onExpire }))
  return { onExpire, ...view }
}

describe('無操作のログアウト（フック）', () => {
  beforeEach(() => {
    localStorage.clear()
    startClock()
  })

  test.each(['keydown', 'pointerdown', 'wheel'])(
    'AC-413-3 %s を操作に数え、30 分の計測を最初から始めて、時刻を共有する',
    (type) => {
      const { onExpire } = renderIdle()
      advanceClock(29 * MINUTE)

      operate(type)
      advanceClock(29 * MINUTE)

      expect(onExpire).not.toHaveBeenCalled()
      expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START + 29 * MINUTE))

      advanceClock(MINUTE)

      expect(onExpire).toHaveBeenCalledTimes(1)
    },
  )

  // API の呼び出しを数えないことは、フックが DOM の操作のイベント（ACTIVITY_EVENTS）だけを受け、fetch を見ないことで決まる
  test('AC-413-4 ポインターの移動は操作に数えず、30 分でログアウトする', () => {
    const { onExpire } = renderIdle()
    advanceClock(29 * MINUTE)

    operate('pointermove')
    advanceClock(MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 他のタブの操作を取り込み、その時刻から 30 分たつまでログアウトしない', () => {
    const { result, onExpire } = renderIdle()
    advanceClock(29 * MINUTE)

    otherTabOperatesAt(START + 29 * MINUTE)
    advanceClock(29 * MINUTE)

    // 他のタブの操作から 29 分なので、ログアウトはせず、警告の間にある
    expect(onExpire).not.toHaveBeenCalled()
    expect(result.current.warning).toBe(true)

    advanceClock(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 他のタブの操作は、警告も閉じる', () => {
    const { result } = renderIdle()
    advanceClock(28 * MINUTE)
    expect(result.current.warning).toBe(true)

    otherTabOperatesAt(START + 28 * MINUTE)

    expect(result.current.warning).toBe(false)
  })

  test('AC-413-5 他のタブの古い時刻では、自分のタブの最後の操作を戻さない', () => {
    const { onExpire } = renderIdle()
    advanceClock(10 * MINUTE)
    operate('keydown')

    otherTabOperatesAt(START)
    advanceClock(29 * MINUTE)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 共有の時刻が 30 分以内なら、マウントのときに取り込み、再マウントしても計測を延ばさない', () => {
    const first = renderIdle()
    advanceClock(20 * MINUTE)
    first.unmount()

    const { onExpire } = renderIdle()

    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START))

    advanceClock(10 * MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-1 共有の時刻が 30 分を過ぎていれば、マウントのときに現在の時刻から計測を始め、共有の値も書き換える', () => {
    localStorage.setItem(LAST_ACTIVITY_KEY, String(START - 30 * MINUTE))

    const { onExpire } = renderIdle()

    expect(onExpire).not.toHaveBeenCalled()
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START))

    advanceClock(30 * MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 別のキーの書き込みは取り込まない', () => {
    const { onExpire } = renderIdle()
    advanceClock(29 * MINUTE)

    act(() => {
      window.dispatchEvent(
        new StorageEvent('storage', { key: 'ec-admin.other', newValue: String(START + 29 * MINUTE) }),
      )
    })
    advanceClock(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 操作の時刻の共有は、前に書いてから 1 秒たつまで書かない', () => {
    renderIdle()
    advanceClock(999)

    operate('keydown')

    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START))

    advanceClock(1)
    operate('keydown')

    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START + 1000))
  })

  test('AC-413-5 ローカルストレージが例外を投げるときは、共有せずにそのタブの中で判定を続ける', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('使えない', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('使えない', 'SecurityError')
    })
    const { onExpire } = renderIdle()
    advanceClock(29 * MINUTE)

    operate('keydown')
    advanceClock(30 * MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test.each(['visibilitychange', 'focus', 'pageshow'] as const)(
    'AC-413-6 スリープの後に再開したとき（%s）、最後の操作から 30 分を過ぎていればログアウトする',
    (wake) => {
      const { onExpire } = renderIdle()

      sleepAndWake(IDLE_LIMIT_MS, wake)

      expect(onExpire).toHaveBeenCalledTimes(1)
    },
  )

  test('AC-413-6 スリープの後に再開したとき、30 分の直前なら、ログアウトしない', () => {
    const { onExpire } = renderIdle()

    sleepAndWake(IDLE_LIMIT_MS - 1)

    expect(onExpire).not.toHaveBeenCalled()
  })

  test('AC-413-6 スリープの後に再開したとき、他のタブが操作していれば、ログアウトしない', () => {
    const { onExpire } = renderIdle()

    act(() => {
      vi.setSystemTime(START + 40 * MINUTE)
      localStorage.setItem(LAST_ACTIVITY_KEY, String(START + 39 * MINUTE))
      document.dispatchEvent(new Event('visibilitychange'))
    })

    expect(onExpire).not.toHaveBeenCalled()
  })

  test('AC-413-6 スリープの後にタブの表示状態が変わらなくても、判定の間隔のうちにログアウトする', () => {
    const { onExpire } = renderIdle()
    sleep(31 * MINUTE)

    advanceClock(IDLE_CHECK_INTERVAL_MS - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-6 スリープの後の最初の操作では、計測を始め直さずにログアウトする', () => {
    const { onExpire } = renderIdle()
    sleep(31 * MINUTE)

    operate('keydown')

    expect(onExpire).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START))
  })

  test('AC-413-1 時計が戻ったときは、判定の間隔のうちに現在の時刻から計測を再開し、共有の値も書き換える', () => {
    const { onExpire } = renderIdle()
    sleep(-DAY)

    advanceClock(IDLE_CHECK_INTERVAL_MS)

    const restartedAt = START - DAY + IDLE_CHECK_INTERVAL_MS
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(restartedAt))

    advanceClock(30 * MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 他のタブの未来の時刻は、判定の間隔のうちに現在の時刻にし、共有の値も書き換える', () => {
    const { onExpire } = renderIdle()

    otherTabOperatesAt(START + DAY)
    advanceClock(IDLE_CHECK_INTERVAL_MS)

    const adoptedAt = START + IDLE_CHECK_INTERVAL_MS
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(adoptedAt))

    advanceClock(30 * MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-7 28 分の直前までは警告を出さず、28 分で警告とログアウトする時刻を返す', () => {
    const { result } = renderIdle()

    advanceClock(28 * MINUTE - 1)

    expect(result.current.warning).toBe(false)

    advanceClock(1)

    expect(result.current.warning).toBe(true)
    expect(result.current.logoutAt).toBe(START + 30 * MINUTE)
  })

  test('AC-413-8 警告の間に操作すると、警告を閉じ、30 分の計測を最初から始める', () => {
    const { result, onExpire } = renderIdle()
    advanceClock(29 * MINUTE)
    expect(result.current.warning).toBe(true)

    operate('keydown')

    expect(result.current.warning).toBe(false)

    advanceClock(29 * MINUTE)

    expect(onExpire).not.toHaveBeenCalled()
    expect(result.current.logoutAt).toBe(START + 59 * MINUTE)
  })

  test('AC-413-8 「操作を続ける」を呼ぶと、警告を閉じ、30 分の計測を最初から始め、時刻を共有する', () => {
    const { result, onExpire } = renderIdle()
    advanceClock(29 * MINUTE)

    act(() => {
      result.current.keepWorking()
    })

    expect(result.current.warning).toBe(false)
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START + 29 * MINUTE))

    advanceClock(29 * MINUTE)

    expect(onExpire).not.toHaveBeenCalled()

    advanceClock(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-1 30 分で onExpire を呼ぶのは 1 回だけ', () => {
    const { onExpire } = renderIdle()

    advanceClock(30 * MINUTE)
    advanceClock(10 * MINUTE)
    sleepAndWake(10 * MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('アンマウントの後は、タイマーとイベントのリスナーを残さない', () => {
    const windowAdded = vi.spyOn(window, 'addEventListener')
    const windowRemoved = vi.spyOn(window, 'removeEventListener')
    const documentAdded = vi.spyOn(document, 'addEventListener')
    const documentRemoved = vi.spyOn(document, 'removeEventListener')
    const { onExpire, unmount } = renderIdle()

    unmount()

    expect(vi.getTimerCount()).toBe(0)
    expect(listeners(windowRemoved.mock.calls)).toEqual(listeners(windowAdded.mock.calls))
    expect(listeners(documentRemoved.mock.calls)).toEqual(listeners(documentAdded.mock.calls))
    advanceClock(30 * MINUTE)
    expect(onExpire).not.toHaveBeenCalled()
  })
})
