import { describe, expect, test, vi } from 'vitest'
import { mergeCartAfterLogin } from '@/api/cart'
import { parseCartMergeNotice } from '@/lib/cartMergeNotice'
import { COOKIE_CART_ID, cart, MEMBER_CART_ID, quoteItem } from '@/test/fixtures'
import { cookieValue, resetRequest } from '@/test/next'
import { http, problem, server } from '@/test/server'

/** ログインで得た access token（セッションの Cookie はまだない） */
const LOGIN_TOKEN = 'login-access-token'

/** 移す元のカートの品目 */
const RED = quoteItem({ variantId: '00000000-0000-0000-0000-000000000211', productName: 'T シャツ' })
const BLUE = quoteItem({
  variantId: '00000000-0000-0000-0000-000000000212',
  productName: 'パーカー',
  axisValues: [{ name: '色', value: '青' }],
})

describe('ログインの直後にカートを移す', () => {
  test('AC-122 Cookie のカートを、ログインで得た token で会員のカートへ移し、Cookie のカート ID を消す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    let merged: unknown
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', async ({ request, response }) => {
        merged = { authorization: request.headers.get('Authorization'), body: await request.json() }
        return response(200).json({ cartId: MEMBER_CART_ID, truncatedItems: [], skippedItems: [] })
      }),
    )

    await mergeCartAfterLogin(LOGIN_TOKEN)

    expect(merged).toEqual({ authorization: `Bearer ${LOGIN_TOKEN}`, body: { cartId: COOKIE_CART_ID } })
    expect(cookieValue('cart_id')).toBeUndefined()
    expect(cookieValue('cart_merge_notice')).toBeUndefined()
  })

  test('AC-123 切り詰めた品目と移せなかった品目を、移す前の商品名とバリアントで知らせの Cookie に書く', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED, BLUE]))),
      http.post('/me/cart/merge', ({ response }) =>
        response(200).json({
          cartId: MEMBER_CART_ID,
          truncatedItems: [{ variantId: RED.variantId, quantity: 10 }],
          skippedItems: [{ variantId: BLUE.variantId, quantity: 2 }],
        }),
      ),
    )

    await mergeCartAfterLogin(LOGIN_TOKEN)

    expect(parseCartMergeNotice(cookieValue('cart_merge_notice'))).toEqual({
      truncated: { items: [{ label: 'T シャツ（色：赤）', quantity: 10 }], more: 0 },
      skipped: { items: [{ label: 'パーカー（色：青）', quantity: 2 }], more: 0 },
    })
    expect(cookieValue('cart_id')).toBeUndefined()
  })

  test('AC-124 移せなかったときも、Cookie のカート ID を消す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([RED]))),
      http.post('/me/cart/merge', ({ response }) =>
        response(404).json(problem(404, 'NOT_FOUND', '見つかりませんでした。')),
      ),
    )

    await mergeCartAfterLogin(LOGIN_TOKEN)

    expect(cookieValue('cart_id')).toBeUndefined()
    expect(cookieValue('cart_merge_notice')).toBeUndefined()
  })

  test('Cookie のカートがなければ、API を呼ばない', async () => {
    // ハンドラーを渡さないので、API を呼べばテストが失敗する（setup.ts）
    await mergeCartAfterLogin(LOGIN_TOKEN)

    expect(cookieValue('cart_id')).toBeUndefined()
  })
})
