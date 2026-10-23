import { describe, expect, test } from 'vitest'
import { chooseCart, shouldMergeCart } from '@/lib/cartChoice'
import { COOKIE_CART_ID } from '@/test/fixtures'

describe('使うカート', () => {
  test('AC-120 ログインしていなければ、Cookie のカートを使う', () => {
    expect(chooseCart(false, COOKIE_CART_ID)).toEqual({ kind: 'cookie', cartId: COOKIE_CART_ID })
  })

  test('AC-73 ログインしておらず Cookie のカートもなければ、カートはない', () => {
    expect(chooseCart(false, null)).toEqual({ kind: 'none' })
  })

  test('AC-121 ログインしていれば、Cookie のカートがあっても会員のカートを使う', () => {
    expect(chooseCart(true, COOKIE_CART_ID)).toEqual({ kind: 'member' })
    expect(chooseCart(true, null)).toEqual({ kind: 'member' })
  })
})

describe('ログインの直後にカートを移すか', () => {
  test('AC-122 Cookie のカートがあれば移す', () => {
    expect(shouldMergeCart(COOKIE_CART_ID)).toBe(true)
  })

  test('Cookie のカートがなければ移さない', () => {
    expect(shouldMergeCart(null)).toBe(false)
  })
})
