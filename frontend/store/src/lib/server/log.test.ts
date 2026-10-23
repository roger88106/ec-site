import { beforeEach, describe, expect, type MockInstance, test, vi } from 'vitest'
import { log } from '@/lib/server/log'
import { newTraceparent, TRACEPARENT_HEADER, traceIdOf } from '@/lib/server/trace'
import { resetRequest } from '@/test/next'

/** 要求のヘッダーの traceparent（proxy が付けたもの） */
const TRACEPARENT = '00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01'

describe('追跡の ID', () => {
  test('traceparent は W3C の形（版、32 桁の追跡の ID、16 桁の区間の ID、印）', () => {
    expect(newTraceparent()).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/)
    expect(newTraceparent()).not.toBe(newTraceparent())
  })

  test('traceparent から追跡の ID を取り出す。形が違えば null', () => {
    expect(traceIdOf(TRACEPARENT)).toBe('0af7651916cd43dd8448eb211c80319c')
    expect(traceIdOf('broken')).toBeNull()
    expect(traceIdOf(null)).toBeNull()
  })
})

describe('サーバー側のログ', () => {
  let stdout: MockInstance
  let stderr: MockInstance

  beforeEach(() => {
    stdout = vi.spyOn(process.stdout, 'write').mockImplementation(() => true)
    stderr = vi.spyOn(process.stderr, 'write').mockImplementation(() => true)
  })

  /** 書いた 1 行を JSON として読む */
  function written(spy: MockInstance): Record<string, unknown> {
    expect(spy).toHaveBeenCalledTimes(1)
    const line = String(spy.mock.calls[0]?.[0])
    expect(line.endsWith('\n')).toBe(true)
    return JSON.parse(line)
  }

  test('1 行 1 件の JSON に、時刻、レベル、メッセージ、要求の追跡の ID、項目を書く', async () => {
    resetRequest({ headers: { [TRACEPARENT_HEADER]: TRACEPARENT } })

    await log.warn('カートを移せませんでした', { cartId: 'cart-1' })

    const entry = written(stderr)
    expect(entry).toMatchObject({
      level: 'warn',
      message: 'カートを移せませんでした',
      traceId: '0af7651916cd43dd8448eb211c80319c',
      cartId: 'cart-1',
    })
    expect(new Date(String(entry.time)).toISOString()).toBe(entry.time)
  })

  test('info は標準出力、warn と error は標準エラー出力に書く', async () => {
    await log.info('記録')
    expect(written(stdout).level).toBe('info')

    await log.error('予期しない失敗', { error: 'boom' })
    expect(stderr).toHaveBeenCalledTimes(1)
  })

  test('要求に追跡の ID がなければ、traceId は null', async () => {
    resetRequest()

    await log.info('起動の外の記録')

    expect(written(stdout).traceId).toBeNull()
  })
})
