import type { NextRequest } from 'next/server'
import { signIn } from '@/auth'
import { safeReturnTo } from '@/lib/login'

/**
 * IdP のログインの画面へ移る口（店舗画面の構成 第 3、9 節）。<br />
 * クエリ：returnTo（ログインの後に戻る画面）、register=1（会員登録の画面にする）。
 */
export async function GET(request: NextRequest) {
  const query = request.nextUrl.searchParams
  const redirectTo = safeReturnTo(query.get('returnTo'))
  const authorizationParams = query.get('register') === '1' ? { prompt: 'create' } : undefined
  await signIn('keycloak', { redirectTo }, authorizationParams)
}
