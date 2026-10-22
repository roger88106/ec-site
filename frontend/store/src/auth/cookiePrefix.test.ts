import { describe, expect, test, vi } from 'vitest'
import { authState } from '@/test/auth'
import { cookieValue, resetRequest } from '@/test/next'

/** 接頭辞を指定した店舗画面。環境変数を読むのは auth.ts と cartCookie.ts を読み込むときなので、決めてから読み込む（置き換えは読み直さない） */
vi.stubEnv('STORE_COOKIE_PREFIX', 's1-')
const { getAccessToken } = await import('@/auth')
const { deleteCartId, readCartId, writeCartId, writeCartMergeNotice } = await import('@/api/cartCookie')

describe('店舗画面の Cookie の接頭辞（STORE_COOKIE_PREFIX）', () => {
  test('DC-104-12 Auth.js の Cookie の名前に接頭辞が付き、セッションも同じ名前で読む', async () => {
    authState.token = { accessToken: 'token', expiresAt: Math.floor(Date.now() / 1000) + 3600 }

    await getAccessToken()

    expect(authState.config?.cookies?.sessionToken?.name).toBe('s1-authjs.session-token')
    expect(authState.config?.cookies?.csrfToken?.name).toBe('s1-authjs.csrf-token')
    expect(authState.tokenParams?.cookieName).toBe('s1-authjs.session-token')
  })

  test('DC-104-12 カートの Cookie の名前に接頭辞が付く', async () => {
    resetRequest({ cookies: { 's1-cart_id': '11111111-1111-4111-8111-111111111111', cart_id: 'other' } })

    expect(await readCartId()).toBe('11111111-1111-4111-8111-111111111111')

    await writeCartId('22222222-2222-4222-8222-222222222222')
    await writeCartMergeNotice({ status: 'merged', unavailableCount: 0 } as never)
    expect(cookieValue('s1-cart_id')).toBe('22222222-2222-4222-8222-222222222222')
    expect(cookieValue('s1-cart_merge_notice')).toBeDefined()
    expect(cookieValue('cart_merge_notice')).toBeUndefined()

    await deleteCartId()
    expect(cookieValue('s1-cart_id')).toBeUndefined()
    expect(cookieValue('cart_id')).toBe('other')
  })
})
