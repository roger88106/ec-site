import { describe, expect, test, vi } from 'vitest'
import { nowSeconds } from '@/test/auth'

describe('現在の秒', () => {
  test('偽の時計の現在時刻を、UNIX 時刻の秒で返す', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-16T10:00:00Z'))

    expect(nowSeconds()).toBe(1792144800)
  })

  test('秒に満たない分は切り捨てる', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-16T10:00:00.999Z'))

    expect(nowSeconds()).toBe(1792144800)
  })
})
