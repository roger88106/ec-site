import { notFound, redirect } from 'next/navigation'
import { describe, expect, test } from 'vitest'
import { withActionResult } from '@/actions/withActionResult'
import { connectionFailedMessage } from '@/lib/actionResult'
import { expectNotFound, redirectedTo } from '@/test/next'
import { recordStderr } from '@/test/stderr'

describe('サーバーアクションの処理の包み', () => {
  test('処理が返した値をそのまま返す', async () => {
    expect(await withActionResult(async () => 'checkout-1')).toBe('checkout-1')
    expect(await withActionResult(async () => ({ ok: true }))).toEqual({ ok: true })
  })

  test('AC-307 画面の移動（redirect）の例外は、飲み込まず、ログに出さずに通す', async () => {
    const stderr = recordStderr()

    expect(await redirectedTo(() => withActionResult(async () => redirect('/login')))).toBe('/login')

    expect(stderr.text()).toBe('')
  })

  test('AC-307 not found の例外は、飲み込まず、ログに出さずに通す', async () => {
    const stderr = recordStderr()

    await expectNotFound(() => withActionResult(async () => notFound()))

    expect(stderr.text()).toBe('')
  })

  test('AC-307 例外で失敗したら、error のログに例外の名前とメッセージを出し、「接続できない」の結果を返す', async () => {
    const stderr = recordStderr()

    const result = await withActionResult(async () => {
      throw new TypeError('fetch failed')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
    expect(stderr.entries()).toEqual([
      expect.objectContaining({ level: 'error', errorName: 'TypeError', errorMessage: 'fetch failed' }),
    ])
  })

  test('予期しない例外も、失敗の結果にする（画面を壊さない）', async () => {
    recordStderr()

    const result = await withActionResult(async () => {
      throw new Error('unexpected')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
  })
})
