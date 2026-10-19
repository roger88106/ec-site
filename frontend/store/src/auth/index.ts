import 'server-only'
import { fullName } from '@ec-site/shared/lib/login'
import { headers } from 'next/headers'
import { redirect } from 'next/navigation'
import NextAuth from 'next-auth'
import { getToken } from 'next-auth/jwt'
import Keycloak from 'next-auth/providers/keycloak'
import { cache } from 'react'
import { mergeCartAfterLogin } from '@/api/cart'
import { readServerConfig } from '@/auth/config'
import { logoutUrl } from '@/auth/logout'
import { type MemberJwt, refreshIfNeeded } from '@/auth/refresh'
import { loginPath, safeReturnTo } from '@/lib/login'

/**
 * ログイン（店舗画面の構成 第 9 節）。Auth.js の設定と、ログインの状態・token を読む関数を `@/auth` に集める
 * （ライブラリを替えるときの範囲を狭くするため）。<br />
 * 接続先と secret は環境変数（`config.ts`。本番で値がなければ起動に失敗する）。token の取り直しは `refresh.ts`、
 * ログアウトの URL は `logout.ts`。外からは `@/auth` だけを import する。
 */

/** 接続先と secret */
const {
  issuer: ISSUER,
  clientId: CLIENT_ID,
  clientSecret: CLIENT_SECRET,
  secret: SECRET,
  publicUrl: PUBLIC_URL,
} = readServerConfig()

/** セッションの Cookie を HTTPS だけで送るか（カート ID の Cookie と同じく、本番の起動で true） */
const SECURE_COOKIE = process.env.NODE_ENV === 'production'

/** access token の期限までの残りがこれ以下なら、要求のたびの確かめ（proxy）で取り直す（秒） */
const REFRESH_MARGIN_SECONDS = 60

/** proxy が、いまの画面のパス（クエリを含む）を知らせる要求のヘッダー */
export const PATHNAME_HEADER = 'x-pathname'

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [Keycloak({ issuer: ISSUER, clientId: CLIENT_ID, clientSecret: CLIENT_SECRET })],
  secret: SECRET,
  session: { strategy: 'jwt' },
  useSecureCookies: SECURE_COOKIE,
  trustHost: true,
  events: {
    async signIn({ account }) {
      // ログインの直後に、Cookie のカートを会員のカートへ移す
      if (account?.access_token) {
        await mergeCartAfterLogin(account.access_token)
      }
    },
  },
  callbacks: {
    async jwt({ token, account, profile }): Promise<MemberJwt | null> {
      if (account) {
        // ログインした直後：token と、画面に出す氏名・メールアドレスを持つ
        return {
          ...token,
          name: fullName(profile?.family_name as string | undefined, profile?.given_name as string | undefined),
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
 * IdP のログアウト（RP-Initiated Logout）の URL。ログアウトの後は店舗のトップに戻る。
 * セッションの Cookie を消す前に呼ぶ（ID token を添えるため）。
 */
export async function idpLogoutUrl(): Promise<string> {
  const token = await readToken()
  // 戻り先は公開する URL から作る。リクエストヘッダーのホストは偽れるので使わない
  return logoutUrl(PUBLIC_URL, token?.idToken)
}

/** IdP のアカウントの画面 */
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
