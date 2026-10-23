import { describe, expect, test } from 'vitest'
import { placeOrder } from '@/actions/checkout'
import { ACCESS_TOKEN, signInAs } from '@/test/auth'
import { CHECKOUT_ID, form, ORDER_ID, PAYMENT_PAGE_URL } from '@/test/fixtures'
import { redirectedTo, resetRequest, revalidatedPaths } from '@/test/next'
import { http, problem, server } from '@/test/server'

/** 注文のフォーム（前後に空白のある入力を含む） */
const orderForm = () =>
  form({
    checkoutId: CHECKOUT_ID,
    expectedTotal: '4300',
    name: ' 山田 太郎 ',
    postalCode: '1000001',
    prefectureCode: '13',
    municipalityCode: '13101',
    address: '千代田 1-1',
    phone: '0312345678',
  })

describe('注文する', () => {
  test('AC-143 注文が成立したら、会員の token を付けて送り、決済サービスの支払画面へ移る', async () => {
    signInAs()
    let sent: unknown
    let pageRequestedFor: unknown
    server.use(
      http.post('/checkouts/{checkoutId}/order', async ({ params, request, response }) => {
        sent = {
          checkoutId: params.checkoutId,
          authorization: request.headers.get('Authorization'),
          body: await request.json(),
        }
        return response(201).json({ id: ORDER_ID })
      }),
      http.post('/orders/{orderId}/payment-page', ({ params, response }) => {
        pageRequestedFor = params.orderId
        return response(200).json({ url: PAYMENT_PAGE_URL })
      }),
    )

    expect(await redirectedTo(() => placeOrder(null, orderForm()))).toBe(PAYMENT_PAGE_URL)
    expect(pageRequestedFor).toBe(ORDER_ID)

    expect(sent).toEqual({
      checkoutId: CHECKOUT_ID,
      authorization: `Bearer ${ACCESS_TOKEN}`,
      body: {
        shippingAddress: {
          name: '山田 太郎',
          postalCode: '1000001',
          prefectureCode: '13',
          municipalityCode: '13101',
          address: '千代田 1-1',
          phone: '0312345678',
        },
        expectedTotal: 4300,
      },
    })
  })

  test('AC-143 支払画面の URL を得られなければ、注文の画面へ移る（そこから支払える）', async () => {
    signInAs()
    server.use(
      http.post('/checkouts/{checkoutId}/order', ({ response }) => response(201).json({ id: ORDER_ID })),
      http.post('/orders/{orderId}/payment-page', ({ response }) =>
        response(409).json(problem(409, 'ORDER_NOT_PAYABLE', 'この注文は支払えません。')),
      ),
    )

    expect(await redirectedTo(() => placeOrder(null, orderForm()))).toBe(`/orders/${ORDER_ID}`)
  })

  test('AC-86 入力の形式の誤りは、API のメッセージと欄ごとのメッセージを返す', async () => {
    signInAs()
    server.use(
      http.post('/checkouts/{checkoutId}/order', ({ response }) =>
        response(400).json(
          problem(400, 'INVALID_REQUEST', '入力内容に誤りがあります。', [
            { field: 'shippingAddress.postalCode', message: '郵便番号は 7 桁の数字で入力してください。' },
          ]),
        ),
      ),
    )

    expect(await placeOrder(null, orderForm())).toEqual({
      ok: false,
      message: '入力内容に誤りがあります。',
      errorCode: 'INVALID_REQUEST',
      fieldErrors: { 'shippingAddress.postalCode': '郵便番号は 7 桁の数字で入力してください。' },
    })
    expect(revalidatedPaths).toEqual([])
  })

  test('AC-89 総額が変わっていたら、注文手続きの画面を新しい金額で表示し直す', async () => {
    signInAs()
    server.use(
      http.post('/checkouts/{checkoutId}/order', ({ response }) =>
        response(409).json(
          problem(
            409,
            'TOTAL_AMOUNT_CHANGED',
            '価格または税率が変わったため、総額が変わりました。内容を確かめて、もう一度注文してください。',
          ),
        ),
      ),
    )

    const result = await placeOrder(null, orderForm())

    expect(result).toMatchObject({ ok: false, errorCode: 'TOTAL_AMOUNT_CHANGED' })
    expect(revalidatedPaths).toEqual([`/checkout/${CHECKOUT_ID}`])
  })

  test('会員の token が無効（401）なら、注文手続きの画面に戻るようにログインの画面へ移す', async () => {
    signInAs()
    resetRequest({ headers: { 'x-pathname': `/checkout/${CHECKOUT_ID}` } })
    server.use(
      http.post('/checkouts/{checkoutId}/order', ({ response }) =>
        response(401).json(problem(401, 'UNAUTHENTICATED', 'ログインしてください。')),
      ),
    )

    expect(await redirectedTo(() => placeOrder(null, orderForm()))).toBe(
      `/login?returnTo=${encodeURIComponent(`/checkout/${CHECKOUT_ID}`)}`,
    )
  })
})
