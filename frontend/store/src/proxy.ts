import { NextResponse } from 'next/server'
import { auth, PATHNAME_HEADER } from '@/auth'
import { newTraceparent, TRACEPARENT_HEADER, traceIdOf } from '@/lib/server/trace'

/**
 * 要求ごとにセッションを確認する（店舗画面の構成 第 9 節）。access token の期限が近ければ取り直し、Cookie に書く
 * （サーバーコンポーネントは Cookie を書けないため）。<br />
 * あわせて、いまの画面のパスを要求のヘッダーで知らせる（ログインの後に、その画面へ戻すため）。
 * 追跡の ID（traceparent）も要求ごとに作って知らせる（ログとバックエンドへの要求に同じ ID を付けるため）。
 */
export const proxy = auth((request) => {
  const requestHeaders = new Headers(request.headers)
  requestHeaders.set(PATHNAME_HEADER, request.nextUrl.pathname + request.nextUrl.search)
  // 正しい traceparent が届いていれば、その追跡の ID を引き継ぐ
  if (traceIdOf(request.headers.get(TRACEPARENT_HEADER)) === null) {
    requestHeaders.set(TRACEPARENT_HEADER, newTraceparent())
  }
  return NextResponse.next({ request: { headers: requestHeaders } })
})

/** Auth.js の口と、静的なファイルは除く */
export const config = {
  matcher: ['/((?!api/auth|_next/static|_next/image|favicon.ico).*)'],
}
