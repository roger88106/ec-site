import 'server-only'
import { cookies } from 'next/headers'
import { readServerConfig } from '@/auth/config'
import { cookieName } from '@/auth/cookieNames'

/** Cookie の名前の接頭辞（環境変数 `STORE_COOKIE_PREFIX`。モジュールの読み込み時に 1 回だけ読む） */
const { cookiePrefix: COOKIE_PREFIX } = readServerConfig()

/** ログインの再試行のフラグを持つ Cookie の名前（店舗画面の構成の「ログイン」。接頭辞は環境変数 `STORE_COOKIE_PREFIX`） */
const loginRetryCookie = () => cookieName(COOKIE_PREFIX, 'login-retry')

/** フラグの有効期間（秒） */
const LOGIN_RETRY_MAX_AGE_SECONDS = 600

/**
 * ログインの再試行のフラグがあるかを返す（店舗画面の構成の「ログイン」）。
 *
 * ルートハンドラーから呼ぶ。
 *
 * @returns フラグがあれば true
 */
export async function hasLoginRetryFlag(): Promise<boolean> {
  return (await cookies()).get(loginRetryCookie())?.value === '1'
}

/**
 * ログインの再試行のフラグを書く（値は 1 だけ。token や会員の情報は入れない）。
 *
 * ルートハンドラーからだけ呼べる。
 */
export async function setLoginRetryFlag(): Promise<void> {
  ;(await cookies()).set(loginRetryCookie(), '1', {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: LOGIN_RETRY_MAX_AGE_SECONDS,
  })
}

/**
 * ログインの再試行のフラグを削除する。
 *
 * ルートハンドラーとサーバーアクションからだけ呼べる。
 */
export async function deleteLoginRetryFlag(): Promise<void> {
  ;(await cookies()).delete(loginRetryCookie())
}
