import { type NextRequest, NextResponse } from 'next/server'
import { deleteLoginRetryFlag, hasLoginRetryFlag, PUBLIC_URL, setLoginRetryFlag } from '@/auth'
import { LOGIN_FAILED_PATH, loginRetryPath } from '@/lib/login'

/**
 * Auth.js のエラーの画面の指定先（店舗画面の構成の「ログイン」）。
 *
 * error が Configuration で再試行のフラグがなければ、フラグを書いて、もう 1 回だけログインを試す。
 * それ以外は、フラグを削除して、ログインできなかったことを示す画面へ移す。
 * 移動先は決まったパスだけで、クエリの値は使わない。
 *
 * @param request リクエスト。クエリは error（Auth.js のエラーの種類）
 */
export async function GET(request: NextRequest) {
  if (request.nextUrl.searchParams.get('error') === 'Configuration' && !(await hasLoginRetryFlag())) {
    await setLoginRetryFlag()
    return NextResponse.redirect(`${PUBLIC_URL}${loginRetryPath()}`)
  }
  await deleteLoginRetryFlag()
  return NextResponse.redirect(`${PUBLIC_URL}${LOGIN_FAILED_PATH}`)
}
