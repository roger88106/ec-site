/**
 * 店舗画面のセキュリティヘッダーの値（店舗画面の構成の「セキュリティヘッダー」）。
 *
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
  /** mock の支払画面のオリジン（あるときだけ form-action に許す） */
  mockPaymentOrigin?: string
  /** 商品の画像の配信元のオリジン */
  imageOrigin: string
}

/** 決済サービス（Stripe）の支払画面のオリジン */
const STRIPE_CHECKOUT_ORIGIN = 'https://checkout.stripe.com'

/** HSTS の有効期間（1 年）。サブドメインへの適用とプリロードは、公開のドメインが決まるまで指定しない */
const HSTS_VALUE = 'max-age=31536000'

/**
 * 値が固定のヘッダーを返す。
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
 * CSP の値を作る。
 *
 * 許すオリジンを追加するときは、この関数の入力と `proxy.ts` の呼び出しを変える。環境変数から取るなら、あわせて次も変える。
 * <ul>
 * <li>`auth/config.ts`、環境変数の文書、E2E の起動の設定、テストの期待値</li>
 * <li>スロットの script（`scripts/dev/lib/slot.mjs` とそのテスト。スロットごとに値が変わるとき）</li>
 * <li>店舗画面の構成の「セキュリティヘッダー」の表</li>
 * <li>デプロイの前提（本番で設定する値のとき）</li>
 * </ul>
 *
 * @param input 本番かどうかと、許すオリジンの元になる値
 * @returns `Content-Security-Policy` の値
 */
export function contentSecurityPolicy(input: ContentSecurityPolicyInput): string {
  const directives: Record<string, string[]> = {
    'default-src': ["'self'"],
    // 'unsafe-inline' は、nonce を使わないため（Next.js のインラインのスクリプトとスタイルを止めない）
    'script-src': ["'self'", "'unsafe-inline'", ...(input.production ? [] : ["'unsafe-eval'"])],
    'style-src': ["'self'", "'unsafe-inline'"],
    'img-src': ["'self'", 'data:', new URL(input.imageOrigin).origin],
    'font-src': ["'self'"],
    'connect-src': ["'self'"],
    'frame-ancestors': ["'none'"],
    'base-uri': ["'self'"],
    'object-src': ["'none'"],
    // 外部のオリジン（mock の支払画面は設定したときだけ）を許すのは、JavaScript の読み込みの前に送ったフォームの外部へのリダイレクト（支払画面、IdP のログアウト）を止めないため
    'form-action': [
      "'self'",
      new URL(input.issuer).origin,
      STRIPE_CHECKOUT_ORIGIN,
      ...(input.mockPaymentOrigin ? [new URL(input.mockPaymentOrigin).origin] : []),
    ],
  }
  return Object.entries(directives)
    .map(([name, values]) => `${name} ${values.join(' ')}`)
    .join('; ')
}
