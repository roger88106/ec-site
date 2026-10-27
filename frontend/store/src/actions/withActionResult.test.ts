import { notFound, redirect } from 'next/navigation'
import { describe, expect, test } from 'vitest'
import { withActionResult } from '@/actions/withActionResult'
import { ApiError } from '@/api/result'
import { connectionFailedMessage } from '@/lib/actionResult'
import { expectNotFound, redirectedTo } from '@/test/next'
import { problem } from '@/test/server'
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

  test('AC-307 例外で失敗したら、error のログに出して「接続できない」の結果を返す', async () => {
    const stderr = recordStderr()

    const result = await withActionResult(async () => {
      throw new TypeError('fetch failed')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
    expect(stderr.entries()).toEqual([expect.objectContaining({ level: 'error' })])
  })

  test('NFR-193-1 カートの ID を入れたメッセージの例外でも、ログに ID もメッセージも出さず、例外の名前だけを出す', async () => {
    const stderr = recordStderr()
    const cartId = '3f2b8c1e-5d4a-4e7b-9a60-1c2d3e4f5a6b'
    const message = `カート ${cartId} を作りましたが、Cookie に保存できませんでした`

    await withActionResult(async () => {
      throw new Error(message)
    })

    expect(stderr.text()).not.toContain(cartId)
    expect(stderr.text()).not.toContain(message)
    expect(stderr.entries()).toEqual([
      expect.objectContaining({ level: 'error', errorName: 'Error', causeName: null, causeCode: null }),
    ])
  })

  test('NFR-193-1 原因の例外があれば、その名前とコードを出し、メッセージは出さない', async () => {
    const stderr = recordStderr()

    await withActionResult(async () => {
      throw new TypeError('fetch failed', {
        cause: Object.assign(new Error('connect ECONNREFUSED 127.0.0.1:8080'), { code: 'ECONNREFUSED' }),
      })
    })

    expect(stderr.text()).not.toContain('fetch failed')
    expect(stderr.text()).not.toContain('127.0.0.1')
    expect(stderr.entries()).toEqual([
      expect.objectContaining({ errorName: 'TypeError', causeName: 'Error', causeCode: 'ECONNREFUSED' }),
    ])
  })

  test('NFR-227-1 API の失敗の例外なら、error のログに状態コードを出し、メッセージは出さない', async () => {
    const stderr = recordStderr()

    const result = await withActionResult(async () => {
      throw new ApiError(502, problem(502, 'PAYMENT_GATEWAY_UNAVAILABLE', 'カート 3f2b8c1e を作れません'))
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
    expect(stderr.text()).not.toContain('3f2b8c1e')
    expect(stderr.entries()).toEqual([
      expect.objectContaining({ level: 'error', errorName: 'ApiError', statusCode: 502 }),
    ])
  })

  test('NFR-227-1 状態コードを持たない例外では、状態コードの項目を null にする', async () => {
    const stderr = recordStderr()

    await withActionResult(async () => {
      throw new TypeError('fetch failed')
    })

    expect(stderr.entries()).toEqual([expect.objectContaining({ level: 'error', statusCode: null })])
  })

  test('予期しない例外も、失敗の結果にする（画面を壊さない）', async () => {
    recordStderr()

    const result = await withActionResult(async () => {
      throw new Error('unexpected')
    })

    expect(result).toEqual({ ok: false, message: connectionFailedMessage() })
  })
})
