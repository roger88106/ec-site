import { HttpResponse, http as mswHttp } from 'msw'
import { NextRequest } from 'next/server'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { IDP, server } from '@/test/server'

// Auth.js の本物のリクエストの処理で、IdP への移動の戻り先を確かめる（ほかのテストは Auth.js を置き換えている）
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

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('ログインの戻り先', () => {
  test('AC-241 ログインで IdP へ移るときの戻り先は公開する URL から作り、リクエストヘッダーのホストを使わない', async () => {
    vi.stubEnv('AUTH_URL', PUBLIC_URL)
    server.use(idpDiscovery())
    const { handlers } = await import('@/auth')

    const csrf = await handlers.GET(forgedRequest('/csrf'))
    const { csrfToken } = (await csrf.json()) as { csrfToken: string }
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

    const location = new URL(signIn.headers.get('location') ?? '')
    expect(`${location.origin}${location.pathname}`).toBe(`${IDP}/protocol/openid-connect/auth`)
    expect(location.searchParams.get('redirect_uri')).toBe(`${PUBLIC_URL}/api/auth/callback/keycloak`)
  })
})
