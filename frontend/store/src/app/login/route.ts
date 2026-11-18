import { type NextRequest, NextResponse } from 'next/server'
import { deleteLoginRetryFlag, hasLoginRetryFlag, PUBLIC_URL, signIn } from '@/auth'
import { LOGIN_FAILED_PATH, safeReturnTo } from '@/lib/login'

/**
 * IdP のログインの画面へ移る（店舗画面の構成の「画面の URL」と店舗画面の構成の「ログイン」）。
 *
 * retry=1 は、エラーの後の 1 回だけの再試行（`/login/error` が再試行のフラグを書いた後）。フラグがなければ IdP へ移らず、
 * ログインできなかったことを示す画面へ移す（繰り返さない）。再試行の戻り先は店舗のトップ。
 * retry=1 でない通常のログインは、始めに再試行のフラグを削除する
 * （再試行の途中で IdP がエラーを返したときや、IdP の画面で離れたときに、フラグが残って次の失敗で再試行しなくなるのを防ぐ）。
 *
 * @param request リクエスト。クエリは returnTo（ログインの後に戻る画面）、register=1（会員登録の画面にする）、retry=1（再試行）
 */
export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams
  if (query.get('retry') === '1') {
    if (!(await hasLoginRetryFlag())) {
      return NextResponse.redirect(`${PUBLIC_URL}${LOGIN_FAILED_PATH}`)
    }
    await signIn('keycloak', { redirectTo: '/' }, undefined)
    return
  }
  await deleteLoginRetryFlag()
  const redirectTo = safeReturnTo(query.get('returnTo'))
  const authorizationParams = query.get('register') === '1' ? { prompt: 'create' } : undefined
  await signIn('keycloak', { redirectTo }, authorizationParams)
}
