import { describe, expect, test } from 'vitest'
import { placeOrderErrors } from '@/features/checkout/placeOrderErrors'

describe('注文のエラーの出し方（店舗画面の構成 第 6 節）', () => {
  test('AC-86 入力検証エラーは、API の項目名を入力欄に対応させ、業務の検証エラーと同じ文を出す', () => {
    const errors = placeOrderErrors({
      ok: false,
      message: '入力内容に誤りがあります。',
      errorCode: 'VALIDATION_ERROR',
      fieldErrors: { email: 'メールアドレスの形', 'shippingAddress.postalCode': '郵便番号の形', other: '対応なし' },
    })

    expect(errors.fields).toEqual({
      email: 'メールアドレスの形が正しくありません。',
      postalCode: '郵便番号は数字 7 桁で入力してください。',
    })
    expect(errors.message).toBe('入力内容に誤りがあります。')
  })

  test('AC-86 業務の検証エラーは、エラーコードで入力欄に対応させ、フォームの下には出さない', () => {
    const errors = placeOrderErrors({
      ok: false,
      message: '郵便番号は数字 7 桁で入力してください。',
      errorCode: 'INVALID_POSTAL_CODE',
    })

    expect(errors.fields).toEqual({ postalCode: '郵便番号は数字 7 桁で入力してください。' })
    expect(errors.message).toBeNull()
  })

  test('在庫不足・カートの変更は、カートへのリンクを添える', () => {
    expect(placeOrderErrors({ ok: false, message: '在庫が足りない', errorCode: 'OUT_OF_STOCK' })).toEqual({
      fields: {},
      message: '在庫が足りない',
      backToCart: true,
    })
    expect(placeOrderErrors({ ok: false, message: 'カートが変わった', errorCode: 'CART_CHANGED' }).backToCart).toBe(
      true,
    )
  })

  test('AC-89 総額が変わったときは、メッセージだけを出す（画面は新しい金額で表示し直す）', () => {
    expect(placeOrderErrors({ ok: false, message: '総額が変わりました', errorCode: 'TOTAL_AMOUNT_CHANGED' })).toEqual({
      fields: {},
      message: '総額が変わりました',
      backToCart: false,
    })
  })

  test('結果がない・成功なら、何も出さない', () => {
    expect(placeOrderErrors(null)).toEqual({ fields: {}, message: null, backToCart: false })
    expect(placeOrderErrors({ ok: true })).toEqual({ fields: {}, message: null, backToCart: false })
  })
})
