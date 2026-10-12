import { NextResponse } from 'next/server'
import { auth, PATHNAME_HEADER } from '@/auth'

/**
 * 要求ごとにセッションを確かめる（店舗画面の構成 第 9 節）。access token の期限が近ければ取り直し、Cookie に書く
 * （サーバーコンポーネントは Cookie を書けないため）。<br />
 * あわせて、いまの画面のパスを要求のヘッダーで知らせる（ログインの後に、その画面へ戻すため）。
 */
export const proxy = auth((request) => {
  const requestHeaders = new Headers(request.headers)
  requestHeaders.set(PATHNAME_HEADER, request.nextUrl.pathname + request.nextUrl.search)
  return NextResponse.next({ request: { headers: requestHeaders } })
})

/** Auth.js の口と、静的なファイルは除く */
export const config = {
  matcher: ['/((?!api/auth|_next/static|_next/image|favicon.ico).*)'],
}
