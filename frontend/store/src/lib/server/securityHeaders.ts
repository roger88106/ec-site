/**
 * 店舗画面のセキュリティヘッダーの値（店舗画面の構成の「サーバーの設定とログ」）。<br />
 * `next.config.ts` からも読むので、`server-only` は import しない。
 */

/** ヘッダー 1 つ分 */
type SecurityHeader = { key: string; value: string }

/** CSP の値を作る入力 */
type ContentSecurityPolicyInput = {
  /** 本番のモードか */
  production: boolean
  /** IdP の issuer（オリジンだけを使う） */
  issuer: string
  /** 店舗 API の場所（オリジンだけを使う。mock の支払画面の許可に使う） */
  apiBaseUrl: string
  /** 商品の画像の配信元のオリジン */
  imageOrigin: string
}

/** 決済サービス（Stripe）の支払画面のオリジン */
const STRIPE_CHECKOUT_ORIGIN = 'https://checkout.stripe.com'

/** HSTS の有効期間（1 年）。サブドメインへの適用とプリロードは、公開のドメインが決まるまで指定しない */
const HSTS_VALUE = 'max-age=31536000'

/**
 * 値が固定のヘッダー。
 *
 * @param production 本番のモードか（本番のときだけ HSTS を含める）
 * @returns ヘッダーの一覧
 */
export function fixedSecurityHeaders(production: boolean): SecurityHeader[] {
  const headers: SecurityHeader[] = [
    { key: 'X-Frame-Options', value: 'DENY' },
    { key: 'X-Content-Type-Options', value: 'nosniff' },
    { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  ]
  if (production) {
    headers.push({ key: 'Strict-Transport-Security', value: HSTS_VALUE })
  }
  return headers
}

/**
 * CSP の値。
 *
 * @param input 本番かどうかと、許すオリジンの元になる値
 * @returns `Content-Security-Policy` の値
 */
export function contentSecurityPolicy(input: ContentSecurityPolicyInput): string {
  const directives: Record<string, string[]> = {
    'default-src': ["'self'"],
    'script-src': ["'self'", "'unsafe-inline'", ...(input.production ? [] : ["'unsafe-eval'"])],
    'style-src': ["'self'", "'unsafe-inline'"],
    'img-src': ["'self'", 'data:', new URL(input.imageOrigin).origin],
    'font-src': ["'self'"],
    'connect-src': ["'self'"],
    'frame-ancestors': ["'none'"],
    'base-uri': ["'self'"],
    'object-src': ["'none'"],
    'form-action': ["'self'", new URL(input.issuer).origin, STRIPE_CHECKOUT_ORIGIN, new URL(input.apiBaseUrl).origin],
  }
  return Object.entries(directives)
    .map(([name, values]) => `${name} ${values.join(' ')}`)
    .join('; ')
}
