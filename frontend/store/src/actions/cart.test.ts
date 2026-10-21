import { HttpResponse, http as mswHttp } from 'msw'
import { describe, expect, test } from 'vitest'
import { addToCart, changeCartItemQuantity, removeCartItem, startCheckout } from '@/actions/cart'
import { connectionFailedMessage } from '@/lib/actionResult'
import { ACCESS_TOKEN, signInAs } from '@/test/auth'
import { CHECKOUT_ID, COOKIE_CART_ID, form, MEMBER_CART_ID, NEW_CART_ID, VARIANT_ID } from '@/test/fixtures'
import { cookieOptions, cookieValue, redirectedTo, resetRequest, revalidatedPaths } from '@/test/next'
import { http, problem, STORE_API, server } from '@/test/server'

/** カートに入れるフォーム */
const addForm = () => form({ variantId: VARIANT_ID, quantity: '2' })

/** 会員のカートの ID を返す API（GET /me/cart） */
const memberCart = () => http.get('/me/cart', ({ response }) => response(200).json({ cartId: MEMBER_CART_ID }))

describe('カートに入れる（ゲスト）', () => {
  test('AC-73 カートがなければ作って品目を入れ、カート ID を Cookie に書く', async () => {
    let added: unknown
    server.use(
      http.post('/carts', ({ response }) => response(201).json({ id: NEW_CART_ID })),
      http.post('/carts/{cartId}/items', async ({ params, request, response }) => {
        added = { cartId: params.cartId, body: await request.json() }
        return response(204).empty()
      }),
    )

    expect(await addToCart(null, addForm())).toEqual({ ok: true })

    expect(added).toEqual({ cartId: NEW_CART_ID, body: { variantId: VARIANT_ID, quantity: 2 } })
    expect(cookieValue('cart_id')).toBe(NEW_CART_ID)
    expect(cookieOptions('cart_id')).toMatchObject({ httpOnly: true, sameSite: 'lax', maxAge: 30 * 24 * 60 * 60 })
    expect(revalidatedPaths).toEqual(['/cart'])
  })

  test('AC-120 ログインしていなければ Cookie のカートに入れ、Cookie の有効期限を延ばす', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(
      http.post('/carts/{cartId}/items', ({ params, response }) =>
        params.cartId === COOKIE_CART_ID ? response(204).empty() : response(404).json(problem(404, 'NOT_FOUND', '')),
      ),
    )

    expect(await addToCart(null, addForm())).toEqual({ ok: true })

    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
    expect(cookieOptions('cart_id')).toMatchObject({ maxAge: 30 * 24 * 60 * 60 })
  })

  test('Cookie のカートがもうなければ、カートを再作成して 1 回だけやり直す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(
      http.post('/carts', ({ response }) => response(201).json({ id: NEW_CART_ID })),
      http.post('/carts/{cartId}/items', ({ params, response }) =>
        params.cartId === NEW_CART_ID
          ? response(204).empty()
          : response(404).json(problem(404, 'NOT_FOUND', '見つかりませんでした。')),
      ),
    )

    expect(await addToCart(null, addForm())).toEqual({ ok: true })

    expect(cookieValue('cart_id')).toBe(NEW_CART_ID)
  })

  test('AC-75 数量の範囲の誤りは、API のメッセージと欄ごとのメッセージを返す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(
      http.post('/carts/{cartId}/items', ({ response }) =>
        response(400).json(
          problem(400, 'INVALID_REQUEST', '入力内容に誤りがあります。', [
            { field: 'quantity', message: '数量は 1 以上にしてください。' },
          ]),
        ),
      ),
    )

    expect(await addToCart(null, addForm())).toEqual({
      ok: false,
      message: '入力内容に誤りがあります。',
      errorCode: 'INVALID_REQUEST',
      fieldErrors: { quantity: '数量は 1 以上にしてください。' },
    })
    expect(revalidatedPaths).toEqual([])
  })

  test('バックエンドにつながらなければ、通信の失敗を返す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(mswHttp.post(`${STORE_API}/carts/:cartId/items`, () => HttpResponse.error()))

    expect(await addToCart(null, addForm())).toEqual({ ok: false, message: connectionFailedMessage() })
  })
})

describe('カートに入れる（会員）', () => {
  test('AC-121 ログインしていれば、会員の token を付けて会員のカートに入れ、Cookie のカートは使わない', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    let authorization: string | null = null
    server.use(
      memberCart(),
      http.post('/carts/{cartId}/items', ({ params, request, response }) => {
        authorization = request.headers.get('Authorization')
        return params.cartId === MEMBER_CART_ID
          ? response(204).empty()
          : response(404).json(problem(404, 'NOT_FOUND', ''))
      }),
    )

    expect(await addToCart(null, addForm())).toEqual({ ok: true })

    expect(authorization).toBe(`Bearer ${ACCESS_TOKEN}`)
    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
    expect(revalidatedPaths).toEqual(['/cart'])
  })

  test('会員の token が無効（401）なら、いまの画面に戻るようにログインの画面へ移す', async () => {
    signInAs()
    resetRequest({ headers: { 'x-pathname': '/products/abc' } })
    server.use(
      memberCart(),
      http.post('/carts/{cartId}/items', ({ response }) => response(401).json(problem(401, 'UNAUTHENTICATED', ''))),
    )

    expect(await redirectedTo(() => addToCart(null, addForm()))).toBe('/login?returnTo=%2Fproducts%2Fabc')
  })
})

describe('数量の変更と削除', () => {
  test('AC-78 Cookie のカートの品目の数量を変え、Cookie の有効期限を延ばす', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    let body: unknown
    server.use(
      http.put('/carts/{cartId}/items/{variantId}', async ({ params, request, response }) => {
        body = { ...params, ...((await request.json()) as object) }
        return response(204).empty()
      }),
    )

    expect(await changeCartItemQuantity(null, form({ variantId: VARIANT_ID, quantity: '3' }))).toEqual({ ok: true })

    expect(body).toEqual({ cartId: COOKIE_CART_ID, variantId: VARIANT_ID, quantity: 3 })
    expect(cookieOptions('cart_id')).toMatchObject({ maxAge: 30 * 24 * 60 * 60 })
    expect(revalidatedPaths).toEqual(['/cart'])
  })

  test('Cookie のカートがなければ、API を呼ばずにカートが見つからないと返す', async () => {
    const result = await changeCartItemQuantity(null, form({ variantId: VARIANT_ID, quantity: '3' }))

    expect(result).toEqual({ ok: false, message: 'カートが見つかりません。商品をカートに入れ直してください。' })
  })

  test('AC-78 会員のカートの品目を削除する', async () => {
    signInAs()
    let deleted: unknown
    server.use(
      memberCart(),
      http.delete('/carts/{cartId}/items/{variantId}', ({ params, response }) => {
        deleted = params
        return response(204).empty()
      }),
    )

    expect(await removeCartItem(null, form({ variantId: VARIANT_ID }))).toEqual({ ok: true })

    expect(deleted).toEqual({ cartId: MEMBER_CART_ID, variantId: VARIANT_ID })
  })
})

describe('注文手続きに進む', () => {
  test('AC-125 ログインしていなければ、ログインの後にカートへ戻るようにログインの画面へ移す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })

    expect(await redirectedTo(() => startCheckout(null, form({})))).toBe('/login?returnTo=%2Fcart')
  })

  test('AC-82 引き当てられなければ、カートを表示し直してエラーを返す', async () => {
    signInAs()
    server.use(
      memberCart(),
      http.post('/carts/{cartId}/checkouts', ({ response }) =>
        response(409).json(problem(409, 'OUT_OF_STOCK', '在庫が足りない商品があります。カートを確かめてください。')),
      ),
    )

    expect(await startCheckout(null, form({}))).toEqual({
      ok: false,
      message: '在庫が足りない商品があります。カートを確かめてください。',
      errorCode: 'OUT_OF_STOCK',
    })
    expect(revalidatedPaths).toEqual(['/cart'])
  })

  test('AC-82 引き当てたら、注文手続きの画面へ移る', async () => {
    signInAs()
    server.use(
      memberCart(),
      http.post('/carts/{cartId}/checkouts', ({ response }) => response(201).json({ id: CHECKOUT_ID })),
    )

    expect(await redirectedTo(() => startCheckout(null, form({})))).toBe(`/checkout/${CHECKOUT_ID}`)
  })
})
