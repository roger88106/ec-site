import { describe, expect, test } from 'vitest'
import { chooseCart, shouldMergeCart, shouldRetryCartMerge } from '@/lib/cartChoice'
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

describe('カートの画面で、カートを移す処理を再試行するか', () => {
  test('AC-415-2 ログインしていて Cookie のカート ID があれば、再試行する', () => {
    expect(shouldRetryCartMerge(true, COOKIE_CART_ID)).toBe(true)
  })

  test('ログインしていなければ、Cookie のカート ID があっても再試行しない', () => {
    expect(shouldRetryCartMerge(false, COOKIE_CART_ID)).toBe(false)
  })

  test('Cookie のカート ID がなければ、ログインしていても再試行しない', () => {
    expect(shouldRetryCartMerge(true, null)).toBe(false)
  })
})
