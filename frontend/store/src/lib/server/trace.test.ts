import { describe, expect, test } from 'vitest'
import { newTraceparent, traceIdOf } from '@/lib/server/trace'
import { TRACEPARENT } from '@/test/fixtures'

/** 要求のヘッダーの traceparent（proxy が付けたもの） */

describe('追跡の ID', () => {
  test('traceparent は W3C の形（バージョン、32 桁の追跡の ID、16 桁の区間の ID、印）', () => {
    expect(newTraceparent()).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/)
    expect(newTraceparent()).not.toBe(newTraceparent())
  })

  test('traceparent から追跡の ID を取り出す。形が違えば null', () => {
    expect(traceIdOf(TRACEPARENT)).toBe('0af7651916cd43dd8448eb211c80319c')
    expect(traceIdOf('broken')).toBeNull()
    expect(traceIdOf(null)).toBeNull()
  })
})
