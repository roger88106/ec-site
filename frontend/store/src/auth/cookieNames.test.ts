import { describe, expect, test } from 'vitest'
import { authCookies, cookieName, sessionCookieName } from '@/auth/cookieNames'

describe('Cookie の名前の接頭辞', () => {
  test('DC-104-12 接頭辞が空なら、Auth.js に設定を渡さず、既定の名前のままにする', () => {
    expect(authCookies('', false)).toBeUndefined()
    expect(authCookies('', true)).toBeUndefined()
    expect(sessionCookieName('', false)).toBe('authjs.session-token')
    expect(sessionCookieName('', true)).toBe('__Secure-authjs.session-token')
  })

  test('DC-104-12 開発のモード（http）では、Auth.js の全ての Cookie の名前の前に接頭辞を付ける', () => {
    const cookies = authCookies('s1-', false)

    expect(cookies?.sessionToken?.name).toBe('s1-authjs.session-token')
    expect(cookies?.callbackUrl?.name).toBe('s1-authjs.callback-url')
    expect(cookies?.csrfToken?.name).toBe('s1-authjs.csrf-token')
    expect(cookies?.pkceCodeVerifier?.name).toBe('s1-authjs.pkce.code_verifier')
    expect(cookies?.state?.name).toBe('s1-authjs.state')
    expect(cookies?.nonce?.name).toBe('s1-authjs.nonce')
    expect(cookies?.webauthnChallenge?.name).toBe('s1-authjs.challenge')
    expect(sessionCookieName('s1-', false)).toBe('s1-authjs.session-token')
  })

  test('DC-104-12 本番（https）では、ブラウザーが見る __Secure-・__Host- を先頭に残して、その後ろに接頭辞を付ける', () => {
    const cookies = authCookies('s1-', true)

    expect(cookies?.sessionToken?.name).toBe('__Secure-s1-authjs.session-token')
    expect(cookies?.callbackUrl?.name).toBe('__Secure-s1-authjs.callback-url')
    expect(cookies?.csrfToken?.name).toBe('__Host-s1-authjs.csrf-token')
    expect(cookies?.pkceCodeVerifier?.name).toBe('__Secure-s1-authjs.pkce.code_verifier')
    expect(sessionCookieName('s1-', true)).toBe('__Secure-s1-authjs.session-token')
  })

  test('DC-104-12 名前だけを渡し、属性は Auth.js の既定のままにする', () => {
    for (const cookie of Object.values(authCookies('s1-', true) ?? {})) {
      expect(Object.keys(cookie)).toEqual(['name'])
    }
  })

  test('DC-104-12 カートの Cookie の名前の前に接頭辞を付ける。空なら今の名前のまま', () => {
    expect(cookieName('s2-', 'cart_id')).toBe('s2-cart_id')
    expect(cookieName('', 'cart_id')).toBe('cart_id')
    expect(cookieName('', 'cart_merge_notice')).toBe('cart_merge_notice')
  })
})
