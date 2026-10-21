import { describe, expect, test } from 'vitest'
import { toFailure } from '@/lib/actionResult'

describe('API のエラーの応答を、サーバーアクションの失敗にする', () => {
  test('エラーレスポンスの message とエラーコードを使う', () => {
    const body = { status: 409, errorCode: 'OUT_OF_STOCK', message: '在庫が足りない商品があります。', traceId: 't' }

    expect(toFailure(409, body)).toEqual({
      ok: false,
      message: '在庫が足りない商品があります。',
      errorCode: 'OUT_OF_STOCK',
    })
  })

  test('入力検証エラーは、項目名ごとのメッセージにする', () => {
    const body = {
      status: 400,
      errorCode: 'INVALID_REQUEST',
      message: '入力内容に誤りがあります。',
      traceId: 't',
      errors: [{ field: 'shippingAddress.postalCode', message: '形が違います' }],
    }

    expect(toFailure(400, body).fieldErrors).toEqual({ 'shippingAddress.postalCode': '形が違います' })
  })

  test('本文がエラーレスポンスの形でなければ、HTTP の状態を出す', () => {
    expect(toFailure(502, 'Bad Gateway')).toEqual({ ok: false, message: '通信に失敗しました（HTTP 502）' })
  })
})
