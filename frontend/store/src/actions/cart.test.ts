import { describe, expect, test, vi } from 'vitest'
import { addToCart, changeCartItemQuantity, removeCartItem, retryCartMerge, startCheckout } from '@/actions/cart'
import { connectionFailedMessage } from '@/lib/actionResult'
import { ACCESS_TOKEN, signInAs } from '@/test/auth'
import {
  CHECKOUT_ID,
  COOKIE_CART_ID,
  cart,
  form,
  MEMBER_CART_ID,
  NEW_CART_ID,
  ORDER_ID,
  OTHER_ORDER_ID,
  quoteItem,
  VARIANT_ID,
} from '@/test/fixtures'
import {
  cookieOptions,
  cookieValue,
  redirectedTo,
  requestCookieStore,
  resetRequest,
  revalidatedPaths,
} from '@/test/next'
import { http, networkError, problem, STORE_API, server, statusError } from '@/test/server'
import { recordStderr } from '@/test/stderr'

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

  test('NFR-227-1 カートを作る API が失敗したら、通信の失敗を返し、ログに API の状態コードを出す', async () => {
    const stderr = recordStderr()
    server.use(statusError('post', `${STORE_API}/carts`, 500))

    expect(await addToCart(null, addForm())).toEqual({ ok: false, message: connectionFailedMessage() })

    expect(stderr.entries().filter((entry) => entry.level === 'error')).toEqual([
      expect.objectContaining({ errorName: 'ApiError', status: 500 }),
    ])
  })

  test('バックエンドにつながらなければ、通信の失敗を返す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(networkError('post', `${STORE_API}/carts/:cartId/items`))

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
        body = { ...params, ...(await request.json()) }
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

  test('AC-406-7 フォームの acknowledgedOrderIds を、本文で送る', async () => {
    signInAs()
    let body: unknown
    server.use(
      memberCart(),
      http.post('/carts/{cartId}/checkouts', async ({ request, response }) => {
        body = await request.json()
        return response(201).json({ id: CHECKOUT_ID })
      }),
    )
    const data = new FormData()
    data.append('acknowledgedOrderIds', ORDER_ID)
    data.append('acknowledgedOrderIds', OTHER_ORDER_ID)

    expect(await redirectedTo(() => startCheckout(null, data))).toBe(`/checkout/${CHECKOUT_ID}`)

    expect(body).toEqual({ acknowledgedOrderIds: [ORDER_ID, OTHER_ORDER_ID] })
  })

  test('AC-406-7 確認が必要（409）なら確認の画面へ移る。確認した注文を送っても、また 409 なら確認の画面へ移る', async () => {
    signInAs()
    server.use(
      memberCart(),
      http.post('/carts/{cartId}/checkouts', ({ response }) =>
        response(409).json(problem(409, 'PENDING_ORDER_CONFIRMATION_REQUIRED', '支払待ちの注文があります。')),
      ),
    )
    const data = new FormData()
    data.append('acknowledgedOrderIds', ORDER_ID)

    expect(await redirectedTo(() => startCheckout(null, form({})))).toBe('/checkout/pending-orders')
    expect(await redirectedTo(() => startCheckout(null, data))).toBe('/checkout/pending-orders')
  })
})

describe('カートの画面でカートを移す処理を再試行する', () => {
  const RED = quoteItem({ variantId: '00000000-0000-0000-0000-000000000211', productName: 'T シャツ' })

  test('AC-415-3、AC-415-4 移せたら Cookie のカート ID を消し、切り詰めた品目と移せなかった品目を返し、カートの画面を再表示する', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    let authorization: string | null = null
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', ({ request, response }) => {
        authorization = request.headers.get('Authorization')
        return response(200).json({
          cartId: MEMBER_CART_ID,
          truncatedItems: [{ variantId: RED.variantId, quantity: 10 }],
          skippedItems: [],
        })
      }),
    )

    expect(await retryCartMerge()).toEqual({
      kind: 'merged',
      notice: {
        truncated: { items: [{ label: 'T シャツ（色：赤）', quantity: 10 }], more: 0 },
        skipped: { items: [], more: 0 },
      },
    })

    expect(authorization).toBe(`Bearer ${ACCESS_TOKEN}`)
    expect(cookieValue('cart_id')).toBeUndefined()
    expect(revalidatedPaths).toEqual(['/cart'])
  })

  test('AC-415-3、AC-415-4 切り詰めた品目も移せなかった品目もなければ、知らせなしで移せたと返す', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', ({ response }) =>
        response(200).json({ cartId: MEMBER_CART_ID, truncatedItems: [], skippedItems: [] }),
      ),
    )

    expect(await retryCartMerge()).toEqual({ kind: 'merged', notice: null })
    expect(cookieValue('cart_id')).toBeUndefined()
  })

  test.each([500, 503] as const)(
    'AC-415-5 移す API が %i なら、Cookie のカート ID を残して失敗を返す',
    async (status) => {
      signInAs()
      resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
      recordStderr()
      server.use(
        http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
        statusError('post', `${STORE_API}/me/cart/merge`, status),
      )

      expect(await retryCartMerge()).toEqual({ kind: 'failed' })

      expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
      expect(revalidatedPaths).toEqual([])
    },
  )

  test('AC-415-5 移す API が 2xx で本文のない応答を返したら、Cookie のカート ID を残して失敗を返す', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    recordStderr()
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', () => new Response(null, { status: 200 })),
    )

    expect(await retryCartMerge()).toEqual({ kind: 'failed' })

    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
    expect(revalidatedPaths).toEqual([])
  })

  test('AC-415-5 通信が例外になったら、Cookie のカート ID を残して失敗を返す', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    recordStderr()
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      networkError('post', `${STORE_API}/me/cart/merge`),
    )

    expect(await retryCartMerge()).toEqual({ kind: 'failed' })

    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
    expect(revalidatedPaths).toEqual([])
  })

  test('AC-415-5 移す前のカートの取得が例外になったら、移さずに Cookie のカート ID を残して失敗を返す', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    recordStderr()
    // 移す API のハンドラーを渡さないので、呼ばれればテストが失敗する（setup.ts）
    server.use(networkError('get', `${STORE_API}/carts/:cartId`))

    expect(await retryCartMerge()).toEqual({ kind: 'failed' })

    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
    expect(revalidatedPaths).toEqual([])
  })

  test('AC-415-5 移せた後の Cookie の操作が例外になっても、例外にせず失敗を返す', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    recordStderr()
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', ({ response }) =>
        response(200).json({ cartId: MEMBER_CART_ID, truncatedItems: [], skippedItems: [] }),
      ),
    )
    vi.spyOn(requestCookieStore, 'delete').mockImplementation(() => {
      throw new Error('cookie error')
    })

    expect(await retryCartMerge()).toEqual({ kind: 'failed' })
  })

  test.each([404, 400] as const)(
    'AC-415-6 移す API が %i なら、Cookie のカート ID を消して何も返さない',
    async (status) => {
      signInAs()
      resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
      recordStderr()
      server.use(
        http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
        http.post('/me/cart/merge', ({ response }) =>
          response(status).json(problem(status, 'NOT_FOUND', '見つかりませんでした。')),
        ),
      )

      expect(await retryCartMerge()).toEqual({ kind: 'none' })

      expect(cookieValue('cart_id')).toBeUndefined()
      expect(revalidatedPaths).toEqual([])
    },
  )

  test('AC-415-8 移す API が 401 なら、Cookie のカート ID を残し、カートの画面に戻るようにログインの画面へ移す', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    recordStderr()
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', ({ response }) => response(401).json(problem(401, 'UNAUTHENTICATED', ''))),
    )

    expect(await redirectedTo(() => retryCartMerge())).toBe('/login?returnTo=%2Fcart')

    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
    expect(revalidatedPaths).toEqual([])
  })

  test('NFR-415-2 移せなかったときの警告のログに、カートの ID（Cookie の値）を出力しない', async () => {
    signInAs()
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    const stderr = recordStderr()
    // 例外のメッセージに URL（カートの ID）が入る場合
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError(`fetch failed: /carts/${COOKIE_CART_ID}`))

    expect(await retryCartMerge()).toEqual({ kind: 'failed' })

    const logged = stderr.text()
    expect(logged).toContain('カートを移せませんでした')
    expect(logged).not.toContain(COOKIE_CART_ID)
  })

  test('Cookie のカートがなければ、API を呼ばずに何も返さない', async () => {
    signInAs()

    // ハンドラーを渡さないので、API を呼べばテストが失敗する（setup.ts）
    expect(await retryCartMerge()).toEqual({ kind: 'none' })
  })

  test('ログインしていなければ、API を呼ばずに何も返さず、Cookie のカート ID を残す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })

    expect(await retryCartMerge()).toEqual({ kind: 'none' })

    expect(cookieValue('cart_id')).toBe(COOKIE_CART_ID)
  })
})
