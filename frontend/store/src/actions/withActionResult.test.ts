import { notFound, redirect } from 'next/navigation'
import { beforeEach, describe, expect, type MockInstance, test, vi } from 'vitest'
import { withActionResult } from '@/actions/withActionResult'
import { connectionFailedMessage } from '@/lib/actionResult'
import { redirectedTo } from '@/test/next'

describe('サーバーアクションの処理の包み', () => {
  test('処理が返した値をそのまま返す', async () => {
    expect(await withActionResult(async () => 'checkout-1')).toBe('checkout-1')
    expect(await withActionResult(async () => ({ ok: true }))).toEqual({ ok: true })
  })

  test('画面の移動（redirect）の例外は飲み込まずに通す', async () => {
    expect(await redirectedTo(() => withActionResult(async () => redirect('/login')))).toBe('/login')
  })

  test('not found の例外も通す', async () => {
    await expect(withActionResult(async () => notFound())).rejects.toThrow()
  })

  test('接続の失敗は、接続できなかったことを示す失敗の結果にする', async () => {
    const result = await withActionResult(async () => {
      throw new TypeError('fetch failed')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
  })

  test('予期しない例外も、失敗の結果にする（画面を壊さない）', async () => {
    const result = await withActionResult(async () => {
      throw new Error('unexpected')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
  })
})

describe('サーバーアクションの失敗のログ', () => {
  let stderr: MockInstance

  beforeEach(() => {
    stderr = vi.spyOn(process.stderr, 'write').mockImplementation(() => true)
  })

  test('AC-307 例外で失敗したら、error のログに例外の名前とメッセージを出し、「接続できない」の結果を返す', async () => {
    const result = await withActionResult(async () => {
      throw new TypeError('fetch failed')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
    expect(stderr).toHaveBeenCalledTimes(1)
    expect(JSON.parse(String(stderr.mock.calls[0]?.[0]))).toMatchObject({
      level: 'error',
      errorName: 'TypeError',
      errorMessage: 'fetch failed',
    })
  })

  test('AC-307 画面の移動・not found の例外は、ログに出さずに通す', async () => {
    await redirectedTo(() => withActionResult(async () => redirect('/login')))
    await expect(withActionResult(async () => notFound())).rejects.toThrow()

    expect(stderr).not.toHaveBeenCalled()
  })
})
