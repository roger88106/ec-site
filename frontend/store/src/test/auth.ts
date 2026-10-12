import type { NextAuthConfig, Session } from 'next-auth'
import type { JWT } from 'next-auth/jwt'
import { type Mock, vi } from 'vitest'

/**
 * サーバーの処理のテストで、Auth.js（next-auth）の代わりに使うログインの状態（店舗画面の構成 第 7 節）。<br />
 * setup.ts が next-auth・next-auth/jwt をこれに置き換える。auth.ts は本物を使うので、auth.ts が Auth.js に渡した設定
 * （コールバック、イベント）は config から呼んで確かめる。
 */

/** テストの会員の access token */
export const ACCESS_TOKEN = 'member-access-token'

/** Auth.js の代わりの状態 */
export const authState: {
  /** auth() が返すセッション。ログインしていなければ null */
  session: Session | null
  /** セッションの Cookie を復号した token（getToken が返す）。ログインしていなければ null */
  token: JWT | null
  /** auth.ts が NextAuth に渡した設定 */
  config: NextAuthConfig | undefined
  /** signOut の呼び出し */
  signOut: Mock<(...args: unknown[]) => unknown>
} = { session: null, token: null, config: undefined, signOut: vi.fn() }

/**
 * 会員がログインしている状態にする。
 *
 * @param token token の上書き（期限・refresh token など）
 */
export function signInAs(token: Partial<JWT> = {}) {
  authState.session = {
    expires: '2099-01-01T00:00:00.000Z',
    user: { name: '山田 太郎', email: 'member@example.com' },
  }
  authState.token = {
    name: '山田 太郎',
    email: 'member@example.com',
    accessToken: ACCESS_TOKEN,
    refreshToken: 'member-refresh-token',
    idToken: 'member-id-token',
    // 期限は十分先（取り直さない）
    expiresAt: Math.floor(Date.now() / 1000) + 3600,
    ...token,
  }
}

/** ログインしていない状態にする（各テストの前に setup.ts が呼ぶ） */
export function resetAuth() {
  authState.session = null
  authState.token = null
  authState.signOut.mockReset()
}

/** next-auth の代わり。NextAuth に渡した設定を取っておき、auth() はいまの状態を返す */
export const nextAuthMock = {
  default: (config: NextAuthConfig) => {
    authState.config = config
    return {
      handlers: {},
      // proxy.ts の auth(処理) は、セッションを確かめずに処理をそのまま返す
      auth: (handler?: unknown) => (typeof handler === 'function' ? handler : Promise.resolve(authState.session)),
      signIn: vi.fn(),
      signOut: (...args: unknown[]) => authState.signOut(...args),
    }
  },
}

/** next-auth/jwt の代わり。セッションの Cookie を復号した token として、いまの状態を返す */
export const nextAuthJwtMock = {
  getToken: async () => authState.token,
}
