import { describe, expect, test } from 'vitest'
import { payOrder, requestCancellation } from '@/actions/order'
import { ACCESS_TOKEN, signInAs } from '@/test/auth'
import { form, ORDER_ID } from '@/test/fixtures'
import { redirectedTo, resetRequest, revalidatedPaths } from '@/test/next'
import { http, problem, server } from '@/test/server'

/** 決済サービスの支払画面の URL */
const PAYMENT_PAGE_URL = 'https://pay.example/cs_test_1'

describe('キャンセルを申請する', () => {
  test('AC-95 理由を添えて申請し、注文の画面を表示し直す。理由が空なら送らない', async () => {
    signInAs()
    const reasons: unknown[] = []
    server.use(
      http.put('/orders/{orderId}/cancellation-request', async ({ request, response }) => {
        reasons.push((await request.json()).reason)
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

describe('支払う', () => {
  test('AC-151 会員の token を付けて支払画面の URL を求め、その画面へ移る', async () => {
    signInAs()
    let authorization: unknown
    server.use(
      http.post('/orders/{orderId}/payment-page', ({ request, response }) => {
        authorization = request.headers.get('Authorization')
        return response(200).json({ url: PAYMENT_PAGE_URL })
      }),
    )

    expect(await redirectedTo(() => payOrder(null, form({ orderId: ORDER_ID })))).toBe(PAYMENT_PAGE_URL)
    expect(authorization).toBe(`Bearer ${ACCESS_TOKEN}`)
  })

  test('AC-151 支払えない注文（支払済み、期限の後）は、API のメッセージを返し、注文の画面を表示し直す', async () => {
    signInAs()
    server.use(
      http.post('/orders/{orderId}/payment-page', ({ response }) =>
        response(409).json(
          problem(409, 'ORDER_NOT_PAYABLE', 'この注文は支払えません。支払が済んでいるか、支払の期限を過ぎています。'),
        ),
      ),
    )

    expect(await payOrder(null, form({ orderId: ORDER_ID }))).toEqual({
      ok: false,
      message: 'この注文は支払えません。支払が済んでいるか、支払の期限を過ぎています。',
      errorCode: 'ORDER_NOT_PAYABLE',
    })
    expect(revalidatedPaths).toEqual([`/orders/${ORDER_ID}`])
  })

  test('AC-299 決済サービスのエラー（502）なら、やり直せることを示す API のメッセージを返す', async () => {
    signInAs()
    server.use(
      http.post('/orders/{orderId}/payment-page', ({ response }) =>
        response(502).json(
          problem(
            502,
            'PAYMENT_GATEWAY_UNAVAILABLE',
            '決済サービスに接続できませんでした。しばらくしてから、やり直してください。',
          ),
        ),
      ),
    )

    expect(await payOrder(null, form({ orderId: ORDER_ID }))).toEqual({
      ok: false,
      message: '決済サービスに接続できませんでした。しばらくしてから、やり直してください。',
      errorCode: 'PAYMENT_GATEWAY_UNAVAILABLE',
    })
  })
})

describe('会員の token が無効になったとき', () => {
  test.each([
    ['キャンセルの申請', requestCancellation],
    ['支払う', payOrder],
  ])('%s で 401 が返ると、注文の画面に戻るようにログインの画面へ移る', async (_, action) => {
    signInAs()
    resetRequest({ headers: { 'x-pathname': `/orders/${ORDER_ID}` } })
    server.use(
      http.put('/orders/{orderId}/cancellation-request', ({ response }) =>
        response(401).json(problem(401, 'UNAUTHENTICATED', '認証が必要です。')),
      ),
      http.post('/orders/{orderId}/payment-page', ({ response }) =>
        response(401).json(problem(401, 'UNAUTHENTICATED', '認証が必要です。')),
      ),
    )

    expect(await redirectedTo(() => action(null, form({ orderId: ORDER_ID })))).toBe(
      `/login?returnTo=${encodeURIComponent(`/orders/${ORDER_ID}`)}`,
    )
  })
})
