import { type NextRequest, NextResponse } from 'next/server'
import { hasLoginRetryFlag, PUBLIC_URL, signIn } from '@/auth'
import { safeReturnTo } from '@/lib/login'

/**
 * IdP のログインの画面へ移る（店舗画面の構成の「画面の URL」と店舗画面の構成の「ログイン」）。
 *
 * retry=1 は、エラーの後の 1 回だけの再試行（`/login/error` が再試行のフラグを書いた後）。フラグがなければ IdP へ移らず、
 * ログインできなかった画面へ移す（繰り返さない）。再試行の戻り先は店舗のトップ。
 *
 * @param request リクエスト。クエリは returnTo（ログインの後に戻る画面）、register=1（会員登録の画面にする）、retry=1（再試行）
 */
export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams
  if (query.get('retry') === '1') {
    if (!(await hasLoginRetryFlag())) {
      return NextResponse.redirect(`${PUBLIC_URL}/login/failed`)
    }
    await signIn('keycloak', { redirectTo: '/' }, undefined)
    return
  }
  const redirectTo = safeReturnTo(query.get('returnTo'))
  const authorizationParams = query.get('register') === '1' ? { prompt: 'create' } : undefined
  await signIn('keycloak', { redirectTo }, authorizationParams)
}
