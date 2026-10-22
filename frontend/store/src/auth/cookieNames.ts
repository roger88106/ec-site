import type { NextAuthConfig } from 'next-auth'

/**
 * 店舗画面の Cookie の名前に付ける接頭辞（環境変数 `STORE_COOKIE_PREFIX`）。<br />
 * Cookie はポートで分かれないので、同じ localhost で複数の店舗画面を動かすと、互いの Cookie を読み合う。
 * 並行の作業ツリーごとに接頭辞を変えて、これを避ける。空なら今の名前のまま（本番と CI）。
 */

/** Auth.js の Cookie の既定の名前（`__Secure-`・`__Host-` を除く部分）。キーは Auth.js の `cookies` の設定の名前 */
const AUTH_COOKIE_BASE_NAMES = {
  sessionToken: 'authjs.session-token',
  callbackUrl: 'authjs.callback-url',
  csrfToken: 'authjs.csrf-token',
  pkceCodeVerifier: 'authjs.pkce.code_verifier',
  state: 'authjs.state',
  nonce: 'authjs.nonce',
  webauthnChallenge: 'authjs.challenge',
} as const

/** Auth.js の設定の `cookies` で名前を変える Cookie の種類 */
type AuthCookieKind = keyof typeof AUTH_COOKIE_BASE_NAMES

/**
 * Cookie の名前に接頭辞を付ける（Auth.js の Cookie 以外。カートなど）。
 *
 * @param prefix 接頭辞。空なら付けない
 * @param name Cookie の名前
 */
export function cookieName(prefix: string, name: string): string {
  return `${prefix}${name}`
}

/**
 * Auth.js の設定 `cookies`。名前だけを渡し、属性は Auth.js の既定（開発のモードと本番で変わる）のままにする。
 *
 * @param prefix 接頭辞
 * @param secure HTTPS だけで送るか（Auth.js の `useSecureCookies`）
 * @returns 接頭辞が空なら undefined（Auth.js に何も渡さず、既定のままにする）
 */
export function authCookies(prefix: string, secure: boolean): NextAuthConfig['cookies'] {
  if (prefix === '') {
    return undefined
  }
  const kinds = Object.keys(AUTH_COOKIE_BASE_NAMES) as AuthCookieKind[]
  return Object.fromEntries(kinds.map((kind) => [kind, { name: authCookieName(prefix, kind, secure) }]))
}

/**
 * セッションの Cookie の名前。`getToken` で読むときに渡す（Auth.js は名前を JWT の暗号化の salt にも使うので、
 * 名前を変えたら読むときも同じ名前を渡す）。
 *
 * @param prefix 接頭辞
 * @param secure HTTPS だけで送るか
 */
export function sessionCookieName(prefix: string, secure: boolean): string {
  return authCookieName(prefix, 'sessionToken', secure)
}

// ============================== プライベート ==============================

/**
 * Auth.js の Cookie の名前。`__Secure-`・`__Host-` は、ブラウザーが名前の先頭で見るので先頭に残し、
 * その後ろに接頭辞を付ける。HTTPS のときだけ付き、CSRF の Cookie だけが `__Host-`、ほかは `__Secure-`。
 */
function authCookieName(prefix: string, kind: AuthCookieKind, secure: boolean): string {
  const browserPrefix = secure ? (kind === 'csrfToken' ? '__Host-' : '__Secure-') : ''
  return `${browserPrefix}${prefix}${AUTH_COOKIE_BASE_NAMES[kind]}`
}
