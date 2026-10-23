import { describe, expect, test, vi } from 'vitest'
import { CART_NOT_FOUND, cartCookieOptions, parseCartId, withCart } from '@/lib/cartId'
import { COOKIE_CART_ID, NEW_CART_ID } from '@/test/fixtures'

describe('カート ID の Cookie の値', () => {
  test('UUID の形ならカート ID とする', () => {
    expect(parseCartId(COOKIE_CART_ID)).toBe(COOKIE_CART_ID)
  })

  test('Cookie がない・形の違う値なら、カートがないものとする', () => {
    expect(parseCartId(undefined)).toBeNull()
    expect(parseCartId('')).toBeNull()
    expect(parseCartId('not-a-uuid')).toBeNull()
  })

  test('HttpOnly・SameSite=Lax で、有効期限はカートと同じ 30 日', () => {
    expect(cartCookieOptions(false)).toEqual({
      httpOnly: true,
      sameSite: 'lax',
      secure: false,
      path: '/',
      maxAge: 30 * 24 * 60 * 60,
    })
    expect(cartCookieOptions(true).secure).toBe(true)
  })
})

describe('カートを使う操作（カートがない・見つからないときに再作成する）', () => {
  test('AC-73 カートがなければ作ってから操作する', async () => {
    const create = vi.fn().mockResolvedValue(NEW_CART_ID)
    const run = vi.fn().mockResolvedValue('done')

    const result = await withCart(null, create, run)

    expect(result).toEqual({ cartId: NEW_CART_ID, created: true, value: 'done' })
    expect(run).toHaveBeenCalledWith(NEW_CART_ID)
  })

  test('カートがあれば、作らずにそのカートで操作する', async () => {
    const create = vi.fn()
    const run = vi.fn().mockResolvedValue('done')

    const result = await withCart(COOKIE_CART_ID, create, run)

    expect(result).toEqual({ cartId: COOKIE_CART_ID, created: false, value: 'done' })
    expect(create).not.toHaveBeenCalled()
  })

  test('カートが見つからなければ、再作成して 1 回だけやり直す', async () => {
    const create = vi.fn().mockResolvedValue(NEW_CART_ID)
    const run = vi.fn().mockResolvedValueOnce(CART_NOT_FOUND).mockResolvedValueOnce('done')

    const result = await withCart(COOKIE_CART_ID, create, run)

    expect(result).toEqual({ cartId: NEW_CART_ID, created: true, value: 'done' })
    expect(run).toHaveBeenNthCalledWith(1, COOKIE_CART_ID)
    expect(run).toHaveBeenNthCalledWith(2, NEW_CART_ID)
  })

  test('作ったカートでも見つからなければ、エラーにする', async () => {
    const create = vi.fn().mockResolvedValue(NEW_CART_ID)
    const run = vi.fn().mockResolvedValue(CART_NOT_FOUND)

    await expect(withCart(COOKIE_CART_ID, create, run)).rejects.toThrow(
      `作ったカートが見つかりません（${NEW_CART_ID}）`,
    )
    expect(create).toHaveBeenCalledTimes(1)
  })
})
