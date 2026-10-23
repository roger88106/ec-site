import 'next-auth'
import 'next-auth/jwt'
import 'next/headers'
import { describe, expect, test, vi } from 'vitest'
import { parseCartMergeNotice } from '@/lib/cartMergeNotice'
import { authState, signInAs } from '@/test/auth'
import { COOKIE_CART_ID, NEW_CART_ID } from '@/test/fixtures'
import { cookieValue, resetRequest } from '@/test/next'

/**
 * 接頭辞を決めて、auth.ts と cartCookie.ts を読み込み直す。環境変数を読むのは読み込むときなので、決めてから読み込む。
 * テストの補助（Auth.js の代わりの状態、Cookie）は読み直さずに同じものを使う。そのため、置き換えたモジュール
 * （next-auth、next/headers）を先に読み込んで、補助との結び付きを決めておく。
 *
 * @param prefix STORE_COOKIE_PREFIX の値。空なら指定しない
 */
async function loadWithPrefix(prefix: string) {
  vi.resetModules()
  vi.stubEnv('STORE_COOKIE_PREFIX', prefix)
  return { auth: await import('@/auth'), cartCookie: await import('@/api/cartCookie') }
}

describe('店舗画面の Cookie の接頭辞（STORE_COOKIE_PREFIX）', () => {
  test('DC-104-12 Auth.js の Cookie の名前に接頭辞が付き、セッションも同じ名前で読む', async () => {
    const { auth } = await loadWithPrefix('s1-')
    signInAs()

    await auth.getAccessToken()

    expect(authState.config?.cookies?.sessionToken?.name).toBe('s1-authjs.session-token')
    expect(authState.config?.cookies?.csrfToken?.name).toBe('s1-authjs.csrf-token')
    expect(authState.tokenParams?.cookieName).toBe('s1-authjs.session-token')
  })

  test('DC-104-12 接頭辞を指定しなければ、Auth.js に Cookie の設定を渡さず、既定の名前でセッションを読む', async () => {
    const { auth } = await loadWithPrefix('')
    signInAs()

    await auth.getAccessToken()

    expect(authState.config?.cookies).toBeUndefined()
    expect(authState.tokenParams?.cookieName).toBe('authjs.session-token')
  })

  test('DC-104-12 カートの Cookie の名前に接頭辞が付く', async () => {
    const { cartCookie } = await loadWithPrefix('s1-')
    resetRequest({ cookies: { 's1-cart_id': COOKIE_CART_ID, cart_id: 'other' } })
    const notice = {
      truncated: { items: [{ label: '商品 A', quantity: 3 }], more: 0 },
      skipped: { items: [], more: 0 },
    }

    expect(await cartCookie.readCartId()).toBe(COOKIE_CART_ID)

    await cartCookie.writeCartId(NEW_CART_ID)
    await cartCookie.writeCartMergeNotice(notice)
    expect(cookieValue('s1-cart_id')).toBe(NEW_CART_ID)
    expect(parseCartMergeNotice(cookieValue('s1-cart_merge_notice'))).toEqual(notice)
    expect(cookieValue('cart_merge_notice')).toBeUndefined()

    await cartCookie.deleteCartId()
    expect(cookieValue('s1-cart_id')).toBeUndefined()
    expect(cookieValue('cart_id')).toBe('other')
  })
})
