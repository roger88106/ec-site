import 'server-only'
import { headers } from 'next/headers'
import { redirect } from 'next/navigation'
import NextAuth from 'next-auth'
import { getToken, type JWT } from 'next-auth/jwt'
import Keycloak from 'next-auth/providers/keycloak'
import { cache } from 'react'
import { loginPath, memberName, needsTokenRefresh, safeReturnTo } from '@/lib/login'

/**
 * ログイン（店舗画面の構成 第 9 節）。Auth.js の設定と、ログインの状態・token を読む関数をこのファイルに集める
 * （ライブラリを替えるときの範囲を狭くするため。plan 003 第 4 節）。<br />
 * 接続先と secret は環境変数。既定はローカルと CI だけで使う値（realm の JSON と同じ。plan 003 第 2.4 節）で、
 * 本番相当の環境では必ず設定する（constitution 5-8）。
 */

/** 消費者の realm の issuer */
const ISSUER = process.env.AUTH_KEYCLOAK_ISSUER ?? 'http://localhost:8180/realms/ec-store'

/** 店舗画面の client（confidential） */
const CLIENT_ID = process.env.AUTH_KEYCLOAK_ID ?? 'ec-store-web'

/** 店舗画面の client の secret */
const CLIENT_SECRET = process.env.AUTH_KEYCLOAK_SECRET ?? 'ec-store-web-local-secret'

/** セッションの Cookie を暗号化する鍵 */
const SECRET = process.env.AUTH_SECRET ?? 'ec-store-local-auth-secret-only-for-local-and-ci'

/** セッションの Cookie を HTTPS だけで送るか（カート ID の Cookie と同じく、本番の起動で true） */
const SECURE_COOKIE = process.env.NODE_ENV === 'production'

/** access token の期限までの残りがこれ以下なら、要求のたびの確かめ（proxy）で取り直す（秒） */
const REFRESH_MARGIN_SECONDS = 60

/** proxy が、いまの画面のパス（クエリを含む）を知らせる要求のヘッダー */
export const PATHNAME_HEADER = 'x-pathname'

/** セッションの Cookie に持つ値（token はサーバーだけで扱い、画面から読める session には入れない） */
type MemberJwt = JWT & {
  accessToken?: string
  refreshToken?: string
  idToken?: string
  /** access token の期限（UNIX 時刻の秒） */
  expiresAt?: number
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [Keycloak({ issuer: ISSUER, clientId: CLIENT_ID, clientSecret: CLIENT_SECRET })],
  secret: SECRET,
  session: { strategy: 'jwt' },
  useSecureCookies: SECURE_COOKIE,
  trustHost: true,
  callbacks: {
    async jwt({ token, account, profile }): Promise<MemberJwt | null> {
      if (account) {
        // ログインした直後：token と、画面に出す氏名・メールアドレスを持つ
        return {
          ...token,
          name: memberName(profile?.family_name as string | undefined, profile?.given_name as string | undefined),
          email: profile?.email ?? token.email,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          idToken: account.id_token,
          expiresAt: account.expires_at,
        }
      }
      return refreshIfNeeded(token as MemberJwt, REFRESH_MARGIN_SECONDS)
    },
    session({ session, token }) {
      // 画面から読める情報には、氏名とメールアドレスだけを入れる（/api/auth/session はブラウザーからも読める）
      return {
        expires: session.expires,
        user: { name: token.name ?? '', email: token.email ?? '' },
      } as typeof session
    },
  },
})

/** ログインしている会員（画面に出す情報） */
export type CurrentMember = {
  /** 氏名（姓 名） */
  name: string
  /** メールアドレス */
  email: string
}

/**
 * ログインしている会員。サーバーコンポーネントとサーバーアクションから呼ぶ。要求の中では 1 回だけ読む。
 *
 * @returns ログインしていなければ（IdP のログインの状態が切れて token を取り直せないときも）null
 */
export const currentMember = cache(async (): Promise<CurrentMember | null> => {
  const session = await auth()
  if (!session?.user) {
    return null
  }
  return { name: session.user.name ?? '', email: session.user.email ?? '' }
})

/**
 * 会員の access token（サーバー専用）。期限が切れていれば、その場で取り直して使う（Cookie には書かない。
 * 次の要求で proxy が書く）。要求の中では 1 回だけ読む。
 *
 * @returns ログインしていない、または取り直せなければ null
 */
export const getAccessToken = cache(async (): Promise<string | null> => {
  const token = await readToken()
  if (token === null) {
    return null
  }
  return (await refreshIfNeeded(token, 0))?.accessToken ?? null
})

/**
 * ログインの画面へ移す（店舗画面の構成 第 9 節）。ログインの後は、指定の画面（なければいまの画面）に戻る。
 *
 * @param returnTo ログインの後に戻る画面
 */
export async function redirectToLogin(returnTo?: string): Promise<never> {
  const path = returnTo ?? (await headers()).get(PATHNAME_HEADER)
  redirect(loginPath(safeReturnTo(path)))
}

/**
 * IdP のログアウト（RP-Initiated Logout。AC-115）の URL。ログアウトの後は店舗のトップに戻る。
 * セッションの Cookie を消す前に呼ぶ（ID token を添えるため）。
 */
export async function idpLogoutUrl(): Promise<string> {
  const token = await readToken()
  const requestHeaders = await headers()
  const origin = `${requestHeaders.get('x-forwarded-proto') ?? 'http'}://${requestHeaders.get('host')}`
  const query = new URLSearchParams({ client_id: CLIENT_ID, post_logout_redirect_uri: `${origin}/` })
  if (token?.idToken) {
    query.set('id_token_hint', token.idToken)
  }
  return `${ISSUER}/protocol/openid-connect/logout?${query}`
}

/** IdP のアカウントの画面（AC-119） */
export const ACCOUNT_URL = `${ISSUER}/account`

// ============================== プライベート ==============================

/** セッションの Cookie を復号して読む */
async function readToken(): Promise<MemberJwt | null> {
  return (await getToken({
    req: { headers: await headers() },
    secret: SECRET,
    secureCookie: SECURE_COOKIE,
  })) as MemberJwt | null
}

/**
 * access token の期限が近ければ、refresh token で取り直す。
 *
 * @returns 取り直した（または取り直さなくてよい）token。IdP に断られたら null（ログインしていないものとする。AC-117）
 */
async function refreshIfNeeded(token: MemberJwt, marginSeconds: number): Promise<MemberJwt | null> {
  if (!needsTokenRefresh(token.expiresAt, Math.floor(Date.now() / 1000), marginSeconds)) {
    return token
  }
  if (!token.refreshToken) {
    return null
  }
  try {
    const response = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
      method: 'POST',
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: CLIENT_ID,
        client_secret: CLIENT_SECRET,
        refresh_token: token.refreshToken,
      }),
      cache: 'no-store',
    })
    if (!response.ok) {
      return null
    }
    const refreshed = (await response.json()) as {
      access_token: string
      expires_in: number
      refresh_token?: string
      id_token?: string
    }
    return {
      ...token,
      accessToken: refreshed.access_token,
      expiresAt: Math.floor(Date.now() / 1000) + refreshed.expires_in,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      idToken: refreshed.id_token ?? token.idToken,
    }
  } catch {
    // IdP に届かなかった：ログインの状態は残す（期限が切れていれば、API が 401 を返し、ログインの画面へ移る）
    return token
  }
}
