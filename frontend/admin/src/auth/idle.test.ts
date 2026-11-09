import { beforeEach, describe, expect, test } from 'vitest'
import {
  ACTIVITY_EVENTS,
  IDLE_LIMIT_MS,
  IDLE_WARNING_BEFORE_MS,
  idleStatus,
  LAST_ACTIVITY_KEY,
  logoutAt,
  readLastActivity,
  writeLastActivity,
} from '@/auth/idle'

const MINUTE = 60 * 1000
const START = Date.UTC(2026, 0, 1)

describe('無操作の判定', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  test('AC-413-3 キーの入力、ポインターの押下、ホイールのスクロールを操作に数え、ポインターの移動は数えない', () => {
    expect([...ACTIVITY_EVENTS].sort()).toEqual(['keydown', 'pointerdown', 'wheel'])
  })

  test('AC-413-7 警告は 28 分（ログアウトの 2 分前）で、ログアウトは 30 分', () => {
    expect(IDLE_LIMIT_MS).toBe(30 * MINUTE)
    expect(IDLE_WARNING_BEFORE_MS).toBe(2 * MINUTE)
  })

  test('AC-413-7 28 分の直前までは操作中で、28 分から警告になる', () => {
    expect(idleStatus(START, START)).toBe('active')
    expect(idleStatus(START, START + 28 * MINUTE - 1)).toBe('active')
    expect(idleStatus(START, START + 28 * MINUTE)).toBe('warning')
  })

  test('AC-413-1 30 分の直前までは警告で、30 分から期限切れになる', () => {
    expect(idleStatus(START, START + 30 * MINUTE - 1)).toBe('warning')
    expect(idleStatus(START, START + 30 * MINUTE)).toBe('expired')
    expect(idleStatus(START, START + 8 * 60 * MINUTE)).toBe('expired')
  })

  test('AC-413-7 ログアウトする時刻は、最後に操作した時刻の 30 分後', () => {
    expect(logoutAt(START)).toBe(START + 30 * MINUTE)
  })

  test('AC-413-5 最後に操作した時刻は、ローカルストレージの 1 つのキーに、ミリ秒の数値だけを置く', () => {
    writeLastActivity(START)

    expect(LAST_ACTIVITY_KEY.startsWith('ec-admin.')).toBe(true)
    expect(localStorage).toHaveLength(1)
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(START))
    expect(readLastActivity()).toBe(START)
  })

  test('AC-413-5 値がないとき、数値でないときは、読み取りを null にする', () => {
    expect(readLastActivity()).toBeNull()

    localStorage.setItem(LAST_ACTIVITY_KEY, 'abc')

    expect(readLastActivity()).toBeNull()
  })
})
