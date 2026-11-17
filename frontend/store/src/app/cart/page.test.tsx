import { describe, expect, test } from 'vitest'
import CartPage from '@/app/cart/page'
import { signInAs } from '@/test/auth'
import { COOKIE_CART_ID, cart, MEMBER_CART_ID } from '@/test/fixtures'
import { resetRequest } from '@/test/next'
import { renderStatic } from '@/test/render'
import { http, STORE_API, server, statusError } from '@/test/server'

const RETRYING = 'ログインする前のカートを移しています。'

/**
 * カートの画面を DOM に読み込む。
 *
 * @param loggedIn     会員がログインしているか
 * @param cookieCartId Cookie のカート ID（なければ null）
 * @returns カートの画面を読み込んだ DOM
 */
async function renderCartPage(loggedIn: boolean, cookieCartId: string | null) {
  resetRequest(cookieCartId === null ? {} : { cookies: { cart_id: cookieCartId } })
  if (loggedIn) {
    signInAs()
  }
  server.use(
    http.get('/me/cart', ({ response }) => response(200).json({ cartId: MEMBER_CART_ID })),
    http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([]))),
  )
  return renderStatic(await CartPage())
}

describe('カートの画面の、カートを移す再試行', () => {
  test('AC-415-2 ログインしていて Cookie のカート ID があれば、カートを移していることを示す', async () => {
    const view = await renderCartPage(true, COOKIE_CART_ID)

    expect(view.getByRole('status')).toHaveTextContent(RETRYING)
  })

  test('ログインしていなければ、示さない', async () => {
    const view = await renderCartPage(false, COOKIE_CART_ID)

    expect(view.queryByText(RETRYING)).toBeNull()
  })

  test('Cookie のカート ID がなければ、示さない', async () => {
    const view = await renderCartPage(true, null)

    expect(view.queryByText(RETRYING)).toBeNull()
  })
})

describe('カートの画面の表示', () => {
  test('カートに商品がなければ、空であることと商品を探すリンクを示す', async () => {
    const view = await renderCartPage(false, COOKIE_CART_ID)

    expect(view.getByText('カートに商品がありません。')).toBeInTheDocument()
    expect(view.getByRole('link', { name: '商品を探す' })).toHaveAttribute('href', '/products')
  })

  test('Cookie のカートが見つからなければ（404）、空のカートとして示す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    server.use(statusError('get', `${STORE_API}/carts/:cartId`, 404))

    const view = renderStatic(await CartPage())

    expect(view.getByText('カートに商品がありません。')).toBeInTheDocument()
  })
})
