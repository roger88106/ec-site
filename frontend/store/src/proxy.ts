import { NextResponse } from 'next/server'
import { auth, PATHNAME_HEADER } from '@/auth'
import { readServerConfig } from '@/auth/config'
import { contentSecurityPolicy } from '@/lib/server/securityHeaders'
import { newTraceparent, TRACEPARENT_HEADER, traceIdOf } from '@/lib/server/trace'

/** CSP の値（店舗画面の構成の「セキュリティヘッダー」）。環境変数から作るので、モジュールの読み込みのときに 1 回だけ作る */
const { issuer, mockPaymentOrigin, imageOrigin } = readServerConfig()
const CONTENT_SECURITY_POLICY = contentSecurityPolicy({
  production: process.env.NODE_ENV === 'production',
  issuer,
  mockPaymentOrigin,
  imageOrigin,
})

/**
 * リクエストごとにセッションを確認する（店舗画面の構成の「ログイン」）。access token の期限が近ければ取り直し、Cookie に書く
 * （サーバーコンポーネントは Cookie を書けないため）。<br />
 * あわせて、いまの画面のパスをリクエストのヘッダーで知らせる（ログインの後に、その画面へ戻すため）。
 * 追跡の ID（traceparent）もリクエストごとに作って知らせる（ログとバックエンドへのリクエストに同じ ID を付けるため）。<br />
 * 応答には CSP を付ける（店舗画面の構成の「セキュリティヘッダー」。環境変数から作るので、設定ファイルではなくここで付ける）。
 */
export const proxy = auth((request) => {
  const requestHeaders = new Headers(request.headers)
  requestHeaders.set(PATHNAME_HEADER, request.nextUrl.pathname + request.nextUrl.search)
  // 正しい traceparent が届いていれば、その追跡の ID を引き継ぐ
  if (traceIdOf(request.headers.get(TRACEPARENT_HEADER)) === null) {
    requestHeaders.set(TRACEPARENT_HEADER, newTraceparent())
  }
  const response = NextResponse.next({ request: { headers: requestHeaders } })
  response.headers.set('Content-Security-Policy', CONTENT_SECURITY_POLICY)
  return response
})

/** Auth.js の口と、静的なファイルは除く */
export const config = {
  matcher: ['/((?!api/auth|_next/static|_next/image|favicon.ico).*)'],
}
