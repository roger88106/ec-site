import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { IDLE_LIMIT_MS, LAST_ACTIVITY_KEY } from '@/auth/idle'
import { useIdleLogout } from '@/auth/useIdleLogout'

const MINUTE = 60 * 1000
const START = Date.UTC(2026, 0, 1)

/** 時計とタイマーを進める（タイマーが動く通常の経過） */
function advance(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms)
  })
}

/** タイマーを進めずに時刻だけ進め、タブが見えるようになったことを知らせる（PC のスリープからの再開） */
function sleepAndWake(ms: number) {
  act(() => {
    vi.setSystemTime(Date.now() + ms)
    document.dispatchEvent(new Event('visibilitychange'))
  })
}

/** window で操作のイベントを起こす */
function operate(type: string) {
  act(() => {
    window.dispatchEvent(new Event(type))
  })
}

/** 他のタブが最後に操作した時刻を書いたことを、storage イベントで知らせる */
function otherTabOperatesAt(at: number) {
  act(() => {
    localStorage.setItem(LAST_ACTIVITY_KEY, String(at))
    window.dispatchEvent(new StorageEvent('storage', { key: LAST_ACTIVITY_KEY, newValue: String(at) }))
  })
}

function renderIdle() {
  const onExpire = vi.fn()
  const view = renderHook(() => useIdleLogout({ onExpire }))
  return { onExpire, ...view }
}

describe('無操作のログアウト（フック）', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
    vi.setSystemTime(START)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test.each(['keydown', 'pointerdown', 'wheel'])(
    'AC-413-3 %s を操作に数え、30 分の計測を最初から始めて、時刻を共有する',
    (type) => {
      const { onExpire } = renderIdle()
      advance(29 * MINUTE)

      operate(type)
      advance(29 * MINUTE)

      expect(onExpire).not.toHaveBeenCalled()
      expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START + 29 * MINUTE))

      advance(MINUTE)

      expect(onExpire).toHaveBeenCalledTimes(1)
    },
  )

  test('AC-413-4 ポインターの移動と API の呼び出しは操作に数えず、30 分でログアウトする', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'))
    const { onExpire } = renderIdle()
    advance(29 * MINUTE)

    operate('pointermove')
    await fetch('/orders')
    advance(MINUTE - 1)

    expect(onExpire).not.toHaveBeenCalled()

    advance(1)

    expect(onExpire).toHaveBeenCalledTimes(1)
    fetchSpy.mockRestore()
  })

  test('AC-413-5 他のタブの操作を取り込み、その時刻から 30 分たつまでログアウトしない', () => {
    const { result, onExpire } = renderIdle()
    advance(29 * MINUTE)

    otherTabOperatesAt(START + 29 * MINUTE)
    advance(29 * MINUTE)

    // 他のタブの操作から 29 分なので、ログアウトはせず、警告の間にある
    expect(onExpire).not.toHaveBeenCalled()
    expect(result.current.warning).toBe(true)

    advance(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-5 他のタブの操作は、警告も閉じる', () => {
    const { result } = renderIdle()
    advance(28 * MINUTE)
    expect(result.current.warning).toBe(true)

    otherTabOperatesAt(START + 28 * MINUTE)

    expect(result.current.warning).toBe(false)
  })

  test('AC-413-5 別のキーの書き込みは取り込まない', () => {
    const { onExpire } = renderIdle()
    advance(29 * MINUTE)

    act(() => {
      window.dispatchEvent(
        new StorageEvent('storage', { key: 'ec-admin.other', newValue: String(START + 29 * MINUTE) }),
      )
    })
    advance(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-6 スリープの後に再開したとき、最後の操作から 30 分を過ぎていればログアウトする', () => {
    const { onExpire } = renderIdle()

    sleepAndWake(IDLE_LIMIT_MS)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

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

  test('AC-413-6 スリープの後の次のタイマーでも、30 分を過ぎていればログアウトする', () => {
    const { onExpire } = renderIdle()

    act(() => {
      vi.setSystemTime(START + 31 * MINUTE)
      vi.advanceTimersToNextTimer()
    })

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-7 28 分の直前までは警告を出さず、28 分で警告とログアウトする時刻を返す', () => {
    const { result } = renderIdle()

    advance(28 * MINUTE - 1)

    expect(result.current.warning).toBe(false)

    advance(1)

    expect(result.current.warning).toBe(true)
    expect(result.current.logoutAt).toBe(START + 30 * MINUTE)
  })

  test('AC-413-8 警告の間に操作すると、警告を閉じ、30 分の計測を最初から始める', () => {
    const { result, onExpire } = renderIdle()
    advance(29 * MINUTE)
    expect(result.current.warning).toBe(true)

    operate('keydown')

    expect(result.current.warning).toBe(false)

    advance(29 * MINUTE)

    expect(onExpire).not.toHaveBeenCalled()
    expect(result.current.logoutAt).toBe(START + 59 * MINUTE)
  })

  test('AC-413-8 「操作を続ける」を呼ぶと、警告を閉じ、30 分の計測を最初から始め、時刻を共有する', () => {
    const { result, onExpire } = renderIdle()
    advance(29 * MINUTE)

    act(() => {
      result.current.keepWorking()
    })

    expect(result.current.warning).toBe(false)
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START + 29 * MINUTE))

    advance(29 * MINUTE)

    expect(onExpire).not.toHaveBeenCalled()

    advance(MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })

  test('AC-413-1 30 分で onExpire を呼ぶのは 1 回だけ', () => {
    const { onExpire } = renderIdle()

    advance(30 * MINUTE)
    advance(10 * MINUTE)
    sleepAndWake(10 * MINUTE)

    expect(onExpire).toHaveBeenCalledTimes(1)
  })
})
