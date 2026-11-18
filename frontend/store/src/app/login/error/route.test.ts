import { NextRequest } from 'next/server'
import { describe, expect, test, vi } from 'vitest'
import { GET as loginError } from '@/app/login/error/route'
import { readServerConfig } from '@/auth/config'
import { cookieName } from '@/auth/cookieNames'
import { cookieOptions, cookieValue, resetRequest } from '@/test/next'

const { publicUrl: PUBLIC_URL, cookiePrefix: COOKIE_PREFIX } = readServerConfig()

/** 再試行のフラグの Cookie の名前 */
const RETRY_COOKIE = cookieName(COOKIE_PREFIX, 'login-retry')

/** エラーのエンドポイントを呼ぶ。リクエストのホストは公開する URL と別にする（リダイレクトの先に使われないことを確認するため） */
async function callLoginError(query: string): Promise<Response> {
  return await loginError(new NextRequest(`http://evil.test/login/error${query}`))
}

describe('ログインのエラーのエンドポイント', () => {
  test('AC-430-1 error が Configuration で、フラグがなければ、フラグを設定して、再試行のログインへ移す', async () => {
    const response = await callLoginError('?error=Configuration')

    expect(response.headers.get('location')).toBe(`${PUBLIC_URL}/login?retry=1`)
    expect(cookieValue(RETRY_COOKIE)).toBe('1')
    expect(cookieOptions(RETRY_COOKIE)).toMatchObject({ httpOnly: true, sameSite: 'lax', path: '/', maxAge: 600 })
  })

  test('AC-430-1 本番では、フラグの Cookie を HTTPS だけで送る', async () => {
    vi.stubEnv('NODE_ENV', 'production')

    await callLoginError('?error=Configuration')

    expect(cookieOptions(RETRY_COOKIE)).toMatchObject({ secure: true })
  })

  test('NFR-430-3 フラグの Cookie の値は 1 だけで、token や会員の情報を入れない', async () => {
    await callLoginError('?error=Configuration&email=member%40example.com&code=secret-code')

    expect(cookieValue(RETRY_COOKIE)).toBe('1')
  })

  test('AC-430-2 error が Configuration でも、フラグがあれば、フラグを削除して、ログインできなかった画面へ移す', async () => {
    resetRequest({ cookies: { [RETRY_COOKIE]: '1' } })

    const response = await callLoginError('?error=Configuration')

    expect(response.headers.get('location')).toBe(`${PUBLIC_URL}/login/failed`)
    expect(cookieValue(RETRY_COOKIE)).toBeUndefined()
  })

  test.each([
    ['AccessDenied', '?error=AccessDenied'],
    ['error がない', ''],
  ])('AC-430-2 error が %s なら、再試行せずに、ログインできなかった画面へ移す', async (_, query) => {
    const response = await callLoginError(query)

    expect(response.headers.get('location')).toBe(`${PUBLIC_URL}/login/failed`)
    expect(cookieValue(RETRY_COOKIE)).toBeUndefined()
  })

  test('AC-430-2 error が Configuration 以外のときは、あったフラグも削除する', async () => {
    resetRequest({ cookies: { [RETRY_COOKIE]: '1' } })

    await callLoginError('?error=AccessDenied')

    expect(cookieValue(RETRY_COOKIE)).toBeUndefined()
  })

  test.each([
    ['フラグがない', undefined, `${PUBLIC_URL}/login?retry=1`],
    ['フラグがある', '1', `${PUBLIC_URL}/login/failed`],
  ])('NFR-430-2 %s ときも、クエリの戻り先を受け取らず、決まったパスだけへ移す', async (_, flag, expected) => {
    resetRequest({ cookies: flag === undefined ? {} : { [RETRY_COOKIE]: flag } })

    const response = await callLoginError(
      '?error=Configuration&callbackUrl=https%3A%2F%2Fevil.example.com&returnTo=%2Fcart&redirect_uri=https%3A%2F%2Fevil.example.com',
    )

    expect(response.headers.get('location')).toBe(expected)
  })

  test('NFR-430-2 リダイレクトの先は、リクエストヘッダーのホストでなく、公開する URL から作る', async () => {
    const request = new NextRequest('http://evil.test/login/error?error=AccessDenied', {
      headers: { host: 'evil.test', 'x-forwarded-host': 'evil.test', 'x-forwarded-proto': 'https' },
    })

    const response = await loginError(request)

    expect(new URL(response.headers.get('location') ?? '').origin).toBe(new URL(PUBLIC_URL).origin)
  })
})
