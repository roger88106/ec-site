import { describe, expect, test } from 'vitest'
import { contentSecurityPolicy, fixedSecurityHeaders } from '@/lib/server/securityHeaders'

const INPUT = {
  production: true,
  issuer: 'https://idp.example/realms/ec-store',
  apiBaseUrl: 'https://api.example/api/store',
  imageOrigin: 'https://img.example',
}

/** CSP の値をディレクティブ名ごとの値の並びに分ける */
function directives(csp: string): Record<string, string[]> {
  return Object.fromEntries(
    csp
      .split(';')
      .map((part) => part.trim().split(/\s+/))
      .filter((tokens) => tokens[0] !== '')
      .map(([name, ...values]) => [name, values]),
  )
}

describe('値が固定のセキュリティヘッダー', () => {
  test('AC-418-1 本番のモードでも開発のモードでも、フレームの禁止と MIME スニッフィングの禁止と参照元の制限が付く', () => {
    for (const production of [true, false]) {
      const headers = fixedSecurityHeaders(production)

      expect(headers).toContainEqual({ key: 'X-Frame-Options', value: 'DENY' })
      expect(headers).toContainEqual({ key: 'X-Content-Type-Options', value: 'nosniff' })
      expect(headers).toContainEqual({ key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' })
    }
  })

  test('AC-418-3 本番のモードでは HSTS が付く', () => {
    expect(fixedSecurityHeaders(true)).toContainEqual({
      key: 'Strict-Transport-Security',
      value: 'max-age=31536000',
    })
  })

  test('AC-418-3 開発のモードでは HSTS が付かない', () => {
    const keys = fixedSecurityHeaders(false).map((header) => header.key)

    expect(keys).toContain('X-Frame-Options')
    expect(keys).not.toContain('Strict-Transport-Security')
  })
})

describe('CSP の値', () => {
  test('AC-418-2 default-src、base-uri、font-src、object-src、frame-ancestors は表の値になる', () => {
    const csp = directives(contentSecurityPolicy(INPUT))

    expect(csp['default-src']).toEqual(["'self'"])
    expect(csp['base-uri']).toEqual(["'self'"])
    expect(csp['font-src']).toEqual(["'self'"])
    expect(csp['object-src']).toEqual(["'none'"])
    expect(csp['frame-ancestors']).toEqual(["'none'"])
  })

  test('AC-418-2 script-src と style-src は self と unsafe-inline を許す', () => {
    const csp = directives(contentSecurityPolicy(INPUT))

    expect(csp['script-src']).toEqual(["'self'", "'unsafe-inline'"])
    expect(csp['style-src']).toEqual(["'self'", "'unsafe-inline'"])
  })

  test('AC-418-2 開発のモードの script-src だけに unsafe-eval を加える', () => {
    const production = directives(contentSecurityPolicy(INPUT))
    const development = directives(contentSecurityPolicy({ ...INPUT, production: false }))

    expect(production['script-src']).not.toContain("'unsafe-eval'")
    expect(development['script-src']).toEqual(["'self'", "'unsafe-inline'", "'unsafe-eval'"])
    expect(development['style-src']).not.toContain("'unsafe-eval'")
  })

  test('AC-418-2 img-src は self と data: と画像の配信元のオリジンを許す', () => {
    expect(directives(contentSecurityPolicy(INPUT))['img-src']).toEqual(["'self'", 'data:', 'https://img.example'])
  })

  test.each([true, false])('AC-418-2 connect-src は self だけ（本番のモード：%s）', (production) => {
    expect(directives(contentSecurityPolicy({ ...INPUT, production }))['connect-src']).toEqual(["'self'"])
  })

  test('AC-418-2 form-action は self と、IdP、決済サービス、mock の支払画面の 3 つのオリジンを許す', () => {
    expect(directives(contentSecurityPolicy(INPUT))['form-action']).toEqual([
      "'self'",
      'https://idp.example',
      'https://checkout.stripe.com',
      'https://api.example',
    ])
  })

  test('AC-418-2 IdP と店舗 API のパスはオリジンに入らない', () => {
    const csp = contentSecurityPolicy(INPUT)

    expect(csp).not.toContain('/realms/')
    expect(csp).not.toContain('/api/store')
  })

  test('AC-418-2 ポートつきのローカルの URL からは、ポートつきのオリジンを作る', () => {
    const csp = directives(
      contentSecurityPolicy({
        production: false,
        issuer: 'http://localhost:8180/realms/ec-store',
        apiBaseUrl: 'http://localhost:8080/api/store',
        imageOrigin: 'http://localhost:8333',
      }),
    )

    expect(csp['form-action']).toEqual([
      "'self'",
      'http://localhost:8180',
      'https://checkout.stripe.com',
      'http://localhost:8080',
    ])
    expect(csp['img-src']).toContain('http://localhost:8333')
  })
})
