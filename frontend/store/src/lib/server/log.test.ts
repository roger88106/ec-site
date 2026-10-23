import { beforeEach, describe, expect, type MockInstance, test, vi } from 'vitest'
import { log } from '@/lib/server/log'
import { TRACEPARENT_HEADER } from '@/lib/server/trace'
import { TRACEPARENT } from '@/test/fixtures'
import { resetRequest } from '@/test/next'
import { recordStderr, type StderrRecord } from '@/test/stderr'

describe('サーバー側のログ', () => {
  let stdout: MockInstance
  let stderr: StderrRecord

  beforeEach(() => {
    stdout = vi.spyOn(process.stdout, 'write').mockImplementation(() => true)
    stderr = recordStderr()
  })

  /** 標準出力に書いた 1 行を JSON として読む */
  function writtenToStdout(): Record<string, unknown> {
    expect(stdout).toHaveBeenCalledTimes(1)
    const line = String(stdout.mock.calls[0]?.[0])
    expect(line.endsWith('\n')).toBe(true)
    return JSON.parse(line)
  }

  test('1 行 1 件の JSON に、時刻、レベル、メッセージ、要求の追跡の ID、項目を書く', async () => {
    resetRequest({ headers: { [TRACEPARENT_HEADER]: TRACEPARENT } })

    await log.warn('カートを移せませんでした', { cartId: 'cart-1' })

    const [entry] = stderr.entries()
    expect(stderr.entries()).toHaveLength(1)
    expect(stderr.text().endsWith('\n')).toBe(true)
    expect(entry).toMatchObject({
      level: 'warn',
      message: 'カートを移せませんでした',
      traceId: '0af7651916cd43dd8448eb211c80319c',
      cartId: 'cart-1',
    })
    expect(new Date(String(entry?.time)).toISOString()).toBe(entry?.time)
  })

  test('info は標準出力に書き、標準エラー出力には書かない', async () => {
    await log.info('記録')

    expect(writtenToStdout()).toMatchObject({ level: 'info', message: '記録' })
    expect(stderr.entries()).toEqual([])
  })

  test('error は標準エラー出力に、レベルとメッセージと項目を書く', async () => {
    await log.error('予期しない失敗', { error: 'boom' })

    expect(stderr.entries()).toEqual([
      expect.objectContaining({ level: 'error', message: '予期しない失敗', error: 'boom' }),
    ])
    expect(stdout).not.toHaveBeenCalled()
  })

  test('要求に追跡の ID がなければ、traceId は null', async () => {
    resetRequest()

    await log.info('起動の外の記録')

    expect(writtenToStdout().traceId).toBeNull()
  })
})
