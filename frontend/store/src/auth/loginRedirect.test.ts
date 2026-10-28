import { HttpResponse, http as mswHttp } from 'msw'
import { NextRequest } from 'next/server'
import { beforeAll, describe, expect, test, vi } from 'vitest'
import { IDP, server } from '@/test/server'

// Auth.js の本物のリクエストの処理で、IdP への移動の戻り先を確認する（ほかのテストは Auth.js を置き換えている）
vi.unmock('next-auth')
vi.unmock('next-auth/providers/keycloak')

/** 店舗画面を公開する URL（本番と同じく、Auth.js も AUTH_URL を読む） */
const PUBLIC_URL = 'https://shop.example'

/** 偽ったホストのリクエストヘッダー */
const FORGED_HEADERS = { host: 'evil.test', 'x-forwarded-host': 'evil.test', 'x-forwarded-proto': 'https' }

/** IdP の設定の口（OIDC の discovery） */
function idpDiscovery() {
  return mswHttp.get(`${IDP}/.well-known/openid-configuration`, () =>
    HttpResponse.json({
      issuer: IDP,
      authorization_endpoint: `${IDP}/protocol/openid-connect/auth`,
      token_endpoint: `${IDP}/protocol/openid-connect/token`,
      jwks_uri: `${IDP}/protocol/openid-connect/certs`,
      response_types_supported: ['code'],
      subject_types_supported: ['public'],
      id_token_signing_alg_values_supported: ['RS256'],
    }),
  )
}

/** 偽ったホストで、Auth.js の口にリクエストを送る */
function forgedRequest(path: string, init: { method?: string; body?: string; cookie?: string } = {}) {
  const headers: Record<string, string> = { ...FORGED_HEADERS }
  if (init.cookie) {
    headers.cookie = init.cookie
  }
  if (init.body) {
    headers['content-type'] = 'application/x-www-form-urlencoded'
  }
  return new NextRequest(`https://evil.test/api/auth${path}`, {
    method: init.method ?? 'GET',
    headers,
    body: init.body,
  })
}

/** Auth.js の /csrf の応答の本文から、CSRF token を取り出す */
function csrfTokenOf(body: unknown): string {
  if (typeof body === 'object' && body !== null && 'csrfToken' in body && typeof body.csrfToken === 'string') {
    return body.csrfToken
  }
  throw new Error('Auth.js の /csrf の応答に csrfToken がない')
}

describe('ログインの戻り先', () => {
  let handlers: typeof import('@/auth').handlers

  beforeAll(async () => {
    // 読み込みと変換の時間をテストの時間に含めない
    ;({ handlers } = await import('@/auth'))
  })

  test('AC-241 ログインで IdP へ移るときの戻り先は公開する URL から作り、リクエストヘッダーのホストを使わない', async () => {
    vi.stubEnv('AUTH_URL', PUBLIC_URL)
    server.use(idpDiscovery())

    const csrf = await handlers.GET(forgedRequest('/csrf'))
    const csrfToken = csrfTokenOf(await csrf.json())
    const cookie = csrf.headers
      .getSetCookie()
      .map((value) => value.split(';')[0])
      .join('; ')
    const signIn = await handlers.POST(
      forgedRequest('/signin/keycloak', {
        method: 'POST',
        body: new URLSearchParams({ csrfToken }).toString(),
        cookie,
      }),
    )

    const signInUrl = new URL(signIn.headers.get('location') ?? '')
    expect(`${signInUrl.origin}${signInUrl.pathname}`).toBe(`${IDP}/protocol/openid-connect/auth`)
    expect(signInUrl.searchParams.get('redirect_uri')).toBe(`${PUBLIC_URL}/api/auth/callback/keycloak`)
  })
})
