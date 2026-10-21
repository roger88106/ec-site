import { describe, expect, test, vi } from 'vitest'
import { CART_NOT_FOUND, cartCookieOptions, parseCartId, withCart } from '@/lib/cartId'

/** テストで使うカート ID */
const OLD_ID = '11111111-1111-4111-8111-111111111111'
const NEW_ID = '22222222-2222-4222-8222-222222222222'

describe('カート ID の Cookie の値', () => {
  test('UUID の形ならカート ID とする', () => {
    expect(parseCartId(OLD_ID)).toBe(OLD_ID)
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
    const create = vi.fn().mockResolvedValue(NEW_ID)
    const run = vi.fn().mockResolvedValue('done')

    const result = await withCart(null, create, run)

    expect(result).toEqual({ cartId: NEW_ID, created: true, value: 'done' })
    expect(run).toHaveBeenCalledWith(NEW_ID)
  })

  test('カートがあれば、作らずにそのカートで操作する', async () => {
    const create = vi.fn()
    const run = vi.fn().mockResolvedValue('done')

    const result = await withCart(OLD_ID, create, run)

    expect(result).toEqual({ cartId: OLD_ID, created: false, value: 'done' })
    expect(create).not.toHaveBeenCalled()
  })

  test('カートが見つからなければ、再作成して 1 回だけやり直す', async () => {
    const create = vi.fn().mockResolvedValue(NEW_ID)
    const run = vi.fn().mockResolvedValueOnce(CART_NOT_FOUND).mockResolvedValueOnce('done')

    const result = await withCart(OLD_ID, create, run)

    expect(result).toEqual({ cartId: NEW_ID, created: true, value: 'done' })
    expect(run).toHaveBeenNthCalledWith(1, OLD_ID)
    expect(run).toHaveBeenNthCalledWith(2, NEW_ID)
  })

  test('作ったカートでも見つからなければ、エラーにする', async () => {
    const create = vi.fn().mockResolvedValue(NEW_ID)
    const run = vi.fn().mockResolvedValue(CART_NOT_FOUND)

    await expect(withCart(OLD_ID, create, run)).rejects.toThrow()
    expect(create).toHaveBeenCalledTimes(1)
  })
})
