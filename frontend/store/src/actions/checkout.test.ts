import { describe, expect, test } from 'vitest'
import { placeOrder } from '@/actions/checkout'
import { requestCancellation } from '@/actions/order'
import { ACCESS_TOKEN, signInAs } from '@/test/auth'
import { CHECKOUT_ID, form, ORDER_ID } from '@/test/fixtures'
import { redirectedTo, resetRequest, revalidatedPaths } from '@/test/next'
import { http, problem, server } from '@/test/server'

/** 注文のフォーム（前後に空白のある入力を含む） */
const orderForm = () =>
  form({
    checkoutId: CHECKOUT_ID,
    expectedTotal: '4300',
    name: ' 山田 太郎 ',
    postalCode: '1000001',
    prefecture: '東京都',
    address: '千代田区千代田 1-1',
    phone: '0312345678',
  })

describe('注文する', () => {
  test('AC-90 注文が成立したら、会員の token を付けて送り、注文の画面へ移る', async () => {
    signInAs()
    let sent: unknown
    server.use(
      http.post('/checkouts/{checkoutId}/order', async ({ params, request, response }) => {
        sent = {
          checkoutId: params.checkoutId,
          authorization: request.headers.get('Authorization'),
          body: await request.json(),
        }
        return response(201).json({ id: ORDER_ID })
      }),
    )

    expect(await redirectedTo(() => placeOrder(null, orderForm()))).toBe(`/orders/${ORDER_ID}`)

    expect(sent).toEqual({
      checkoutId: CHECKOUT_ID,
      authorization: `Bearer ${ACCESS_TOKEN}`,
      body: {
        shippingAddress: {
          name: '山田 太郎',
          postalCode: '1000001',
          prefecture: '東京都',
          address: '千代田区千代田 1-1',
          phone: '0312345678',
        },
        expectedTotal: 4300,
      },
    })
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

describe('キャンセルを申請する', () => {
  test('AC-95 理由を添えて申請し、注文の画面を表示し直す。理由が空なら送らない', async () => {
    signInAs()
    const reasons: unknown[] = []
    server.use(
      http.put('/orders/{orderId}/cancellation-request', async ({ request, response }) => {
        reasons.push(((await request.json()) as { reason: unknown }).reason)
        return response(204).empty()
      }),
    )

    expect(await requestCancellation(null, form({ orderId: ORDER_ID, reason: ' 間違えて注文した ' }))).toEqual({
      ok: true,
    })
    expect(await requestCancellation(null, form({ orderId: ORDER_ID, reason: '  ' }))).toEqual({ ok: true })

    expect(reasons).toEqual(['間違えて注文した', null])
    expect(revalidatedPaths).toEqual([`/orders/${ORDER_ID}`, `/orders/${ORDER_ID}`])
  })

  test('AC-96 申請できない注文への申請は、API のメッセージを返す', async () => {
    signInAs()
    server.use(
      http.put('/orders/{orderId}/cancellation-request', ({ response }) =>
        response(400).json(
          problem(
            400,
            'CANCELLATION_NOT_REQUESTABLE',
            'この注文はキャンセルを申請できません。キャンセルをご希望の場合は、お問い合わせください。',
          ),
        ),
      ),
    )

    expect(await requestCancellation(null, form({ orderId: ORDER_ID }))).toEqual({
      ok: false,
      message: 'この注文はキャンセルを申請できません。キャンセルをご希望の場合は、お問い合わせください。',
      errorCode: 'CANCELLATION_NOT_REQUESTABLE',
    })
  })
})
