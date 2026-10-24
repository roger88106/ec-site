import { HttpResponse, http as mswHttp } from 'msw'
import { NextRequest } from 'next/server'
import type { NextAuthConfig, Session } from 'next-auth'
import type { JWT } from 'next-auth/jwt'
import { describe, expect, test } from 'vitest'
import { logout } from '@/actions/auth'
import { GET as login } from '@/app/login/route'
import { currentMember, getAccessToken, redirectToLogin } from '@/auth'
import { readServerConfig } from '@/auth/config'
import { ACCESS_TOKEN, authState, nowSeconds, signInAs } from '@/test/auth'
import { COOKIE_CART_ID, cart, MEMBER_CART_ID } from '@/test/fixtures'
import { cookieValue, redirectedTo, resetRequest } from '@/test/next'
import { http, IDP, networkError, server, TOKEN_ENDPOINT } from '@/test/server'

/** auth.ts が Auth.js に渡したコールバック */
function callbacks() {
  const config = authState.config
  if (!config?.callbacks?.jwt || !config.callbacks.session) {
    throw new Error('auth.ts が Auth.js にコールバックを渡していない')
  }
  return { jwt: config.callbacks.jwt, session: config.callbacks.session }
}

/** jwt コールバックの引数 */
type JwtParams = Parameters<ReturnType<typeof callbacks>['jwt']>[0]

/** session コールバックの引数 */
type SessionParams = Parameters<ReturnType<typeof callbacks>['session']>[0]

/** signIn イベントの引数 */
type SignInEvent = Parameters<NonNullable<NonNullable<NextAuthConfig['events']>['signIn']>>[0]

/** jwt コールバックの引数を作る。ログインした直後でなければ account はない */
function jwtParams(overrides: Partial<JwtParams> & Pick<JwtParams, 'token'>): JwtParams {
  return { user: { id: 'subject' }, account: null, ...overrides }
}

/**
 * session コールバックの引数を作る（JWT のセッションなので session と token を渡す）。<br />
 * Auth.js の型は、データベースのセッションの形（session が AdapterSession、user が AdapterUser）との合併で、
 * JWT のセッションの形だけを型に書けないので、ここで 1 回だけ型を合わせる。
 */
function sessionParams(params: { session: Session; token: JWT }): SessionParams {
  return params as SessionParams
}

/** signIn イベントの引数を作る */
function signInEvent(accessToken: string): SignInEvent {
  return {
    user: { id: 'subject' },
    account: { provider: 'keycloak', type: 'oidc', providerAccountId: 'subject', access_token: accessToken },
  }
}

describe('ログイン', () => {
  test('AC-113 ログインの口は、ログインの後に指定の画面へ戻るように IdP へ移る', async () => {
    await login(new NextRequest('http://localhost:3000/login?returnTo=%2Fproducts%3Fpage%3D2'))

    expect(authState.signIn).toHaveBeenCalledWith('keycloak', { redirectTo: '/products?page=2' }, undefined)
  })

  test('AC-113 戻り先がサイトの外なら、トップに戻る', async () => {
    await login(new NextRequest('http://localhost:3000/login?returnTo=%2F%2Fevil.example.com'))

    expect(authState.signIn).toHaveBeenCalledWith('keycloak', { redirectTo: '/' }, undefined)
  })

  test('AC-108 会員登録のときは、IdP の会員登録の画面を開く', async () => {
    await login(new NextRequest('http://localhost:3000/login?returnTo=%2Fcart&register=1'))

    expect(authState.signIn).toHaveBeenCalledWith('keycloak', { redirectTo: '/cart' }, { prompt: 'create' })
  })

  test('ログインの画面へ移すときは、指定がなければいまの画面に戻る', async () => {
    resetRequest({ headers: { 'x-pathname': '/orders?page=2' } })

    expect(await redirectedTo(() => redirectToLogin())).toBe('/login?returnTo=%2Forders%3Fpage%3D2')
  })

  test('AC-114 ログインした直後の token に、画面に出す氏名（姓 名）と IdP の token を持つ', async () => {
    const token = await callbacks().jwt(
      jwtParams({
        token: { email: 'member@example.com' },
        account: {
          provider: 'keycloak',
          type: 'oidc',
          providerAccountId: 'subject',
          access_token: 'access',
          refresh_token: 'refresh',
          id_token: 'id',
          expires_at: 2000000000,
        },
        profile: { family_name: '山田', given_name: '太郎', email: 'member@example.com' },
      }),
    )

    expect(token).toMatchObject({
      name: '山田 太郎',
      email: 'member@example.com',
      accessToken: 'access',
      refreshToken: 'refresh',
      idToken: 'id',
      expiresAt: 2000000000,
    })
  })

  test('画面から読めるセッションには、氏名とメールアドレスだけを入れる（token は入れない）', async () => {
    const session = await callbacks().session(
      sessionParams({
        session: { expires: '2099-01-01T00:00:00.000Z', user: { name: 'x', email: 'x' } },
        token: { name: '山田 太郎', email: 'member@example.com', accessToken: 'access', refreshToken: 'refresh' },
      }),
    )

    expect(session).toEqual({
      expires: '2099-01-01T00:00:00.000Z',
      user: { name: '山田 太郎', email: 'member@example.com' },
    })
  })

  test('AC-122 ログインの直後に、ログインで得た token で Cookie のカートを会員のカートへ移す', async () => {
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })
    let authorization: string | null = null
    server.use(
      http.get('/carts/{cartId}', ({ response }) => response(200).json(cart([]))),
      http.post('/me/cart/merge', ({ request, response }) => {
        authorization = request.headers.get('Authorization')
        return response(200).json({ cartId: MEMBER_CART_ID, truncatedItems: [], skippedItems: [] })
      }),
    )

    await authState.config?.events?.signIn?.(signInEvent('login-access'))

    expect(authorization).toBe('Bearer login-access')
    expect(cookieValue('cart_id')).toBeUndefined()
  })

  test('ログインしている会員を、セッションから読む', async () => {
    expect(await currentMember()).toBeNull()

    signInAs()

    expect(await currentMember()).toEqual({ name: '山田 太郎', email: 'member@example.com' })
  })
})

describe('ログアウト', () => {
  test('AC-115 セッションと Cookie のカート ID を消し、ID token を添えて IdP のログアウトへ移る', async () => {
    signInAs({ idToken: 'member-id-token' })
    resetRequest({ cookies: { cart_id: COOKIE_CART_ID } })

    const url = new URL(await redirectedTo(() => logout()))

    expect(`${url.origin}${url.pathname}`).toBe(`${IDP}/protocol/openid-connect/logout`)
    expect(Object.fromEntries(url.searchParams)).toEqual({
      client_id: 'ec-store-web',
      post_logout_redirect_uri: `${readServerConfig().publicUrl}/`,
      id_token_hint: 'member-id-token',
    })
    expect(authState.signOut).toHaveBeenCalledWith({ redirect: false })
    expect(cookieValue('cart_id')).toBeUndefined()
  })

  test('AC-241 ログアウトの後の戻り先は公開する URL から作り、リクエストヘッダーのホストとプロトコルを使わない', async () => {
    signInAs({ idToken: 'member-id-token' })
    resetRequest({ headers: { host: 'evil.test', 'x-forwarded-proto': 'https', 'x-forwarded-host': 'evil.test' } })

    const url = new URL(await redirectedTo(() => logout()))

    expect(url.searchParams.get('post_logout_redirect_uri')).toBe(`${readServerConfig().publicUrl}/`)
  })
})

describe('token の取り直し', () => {
  test('AC-117 access token の期限が近ければ、refresh token で取り直す', async () => {
    let grant: Record<string, string> | undefined
    server.use(
      mswHttp.post(TOKEN_ENDPOINT, async ({ request }) => {
        grant = Object.fromEntries(new URLSearchParams(await request.text()))
        return HttpResponse.json({ access_token: 'new-access', expires_in: 300, refresh_token: 'new-refresh' })
      }),
    )

    const token = await callbacks().jwt(
      jwtParams({
        token: { accessToken: 'old', refreshToken: 'old-refresh', idToken: 'id', expiresAt: nowSeconds() + 30 },
      }),
    )

    expect(grant).toMatchObject({
      grant_type: 'refresh_token',
      client_id: 'ec-store-web',
      refresh_token: 'old-refresh',
    })
    expect(token).toMatchObject({ accessToken: 'new-access', refreshToken: 'new-refresh', idToken: 'id' })
    expect(token?.expiresAt).toBeGreaterThanOrEqual(nowSeconds() + 299)
  })

  test('期限まで十分あれば、取り直さない', async () => {
    const token = { accessToken: 'current', refreshToken: 'refresh', expiresAt: nowSeconds() + 3600 }

    // ハンドラーを渡さないので、IdP を呼べばテストが失敗する（setup.ts）
    expect(await callbacks().jwt(jwtParams({ token }))).toEqual(token)
  })

  test('AC-117 IdP に取り直しを断られたら（ログインの状態が切れた）、ログインしていないものとする', async () => {
    server.use(mswHttp.post(TOKEN_ENDPOINT, () => HttpResponse.json({ error: 'invalid_grant' }, { status: 400 })))

    const token = await callbacks().jwt(
      jwtParams({ token: { accessToken: 'old', refreshToken: 'old-refresh', expiresAt: nowSeconds() - 10 } }),
    )

    expect(token).toBeNull()
  })

  test('IdP に届かなければ、ログインの状態を残す', async () => {
    server.use(networkError('post', TOKEN_ENDPOINT))
    const token = { accessToken: 'old', refreshToken: 'old-refresh', expiresAt: nowSeconds() - 10 }

    expect(await callbacks().jwt(jwtParams({ token }))).toEqual(token)
  })

  test('API を呼ぶときの access token は、期限が切れていればその場で取り直したものを使う', async () => {
    expect(await getAccessToken()).toBeNull()

    signInAs({ expiresAt: nowSeconds() - 10 })
    server.use(mswHttp.post(TOKEN_ENDPOINT, () => HttpResponse.json({ access_token: 'fresh', expires_in: 300 })))

    expect(await getAccessToken()).toBe('fresh')
  })

  test('期限が切れていなければ、セッションの access token を使う', async () => {
    signInAs()

    expect(await getAccessToken()).toBe(ACCESS_TOKEN)
  })
})
