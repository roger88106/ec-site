import { NextRequest } from 'next/server'
import { describe, expect, test } from 'vitest'
import { GET as login } from '@/app/login/route'
import { readServerConfig } from '@/auth/config'
import { cookieName } from '@/auth/cookieNames'
import { authState } from '@/test/auth'
import { redirectedTo, resetRequest } from '@/test/next'

const { publicUrl: PUBLIC_URL, cookiePrefix: COOKIE_PREFIX } = readServerConfig()

/** 再試行のフラグの Cookie の名前 */
const RETRY_COOKIE = cookieName(COOKIE_PREFIX, 'login-retry')

/**
 * 再試行のログイン（retry=1）を呼び、リダイレクトの先を返す。
 *
 * リダイレクトは、応答の Location か、Next.js の redirect の例外のどちらでもよい。
 */
async function loginLocation(query: string): Promise<string | null> {
  const request = new NextRequest(`http://evil.test/login${query}`)
  try {
    return (await login(request))?.headers.get('location') ?? null
  } catch {
    return await redirectedTo(() => login(request))
  }
}

describe('再試行のログイン', () => {
  test('AC-430-3 フラグの Cookie がなければ、IdP へ移らず、ログインできなかったことを示す画面へ移す', async () => {
    const location = await loginLocation('?retry=1')

    expect(authState.signIn).not.toHaveBeenCalled()
    expect(location).toBe(`${PUBLIC_URL}/login/failed`)
  })

  test('AC-430-1 フラグの Cookie があれば、IdP へ移る。戻る画面は店舗のトップ', async () => {
    resetRequest({ cookies: { [RETRY_COOKIE]: '1' } })

    await loginLocation('?retry=1')

    expect(authState.signIn).toHaveBeenCalledWith('keycloak', { redirectTo: '/' }, undefined)
  })

  test('AC-430-1 再試行のときは、クエリの returnTo があっても、戻る画面は店舗のトップ', async () => {
    resetRequest({ cookies: { [RETRY_COOKIE]: '1' } })

    await loginLocation('?retry=1&returnTo=%2Fcart')

    expect(authState.signIn).toHaveBeenCalledWith('keycloak', { redirectTo: '/' }, undefined)
  })

  test('NFR-430-2 フラグがないときのリダイレクトの先は、クエリの戻り先を受け取らず、決まったパスだけ', async () => {
    const location = await loginLocation('?retry=1&returnTo=https%3A%2F%2Fevil.example.com&callbackUrl=%2Fcart')

    expect(location).toBe(`${PUBLIC_URL}/login/failed`)
  })

  test('retry が 1 以外なら、再試行でなく通常のログインとして扱う', async () => {
    await login(new NextRequest('http://localhost:3000/login?retry=0&returnTo=%2Fcart'))

    expect(authState.signIn).toHaveBeenCalledWith('keycloak', { redirectTo: '/cart' }, undefined)
  })
})
