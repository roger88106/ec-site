import { NextRequest } from 'next/server'
import { describe, expect, test } from 'vitest'
import { config, proxy } from '@/proxy'
import { TRACEPARENT } from '@/test/fixtures'

/**
 * proxy を呼ぶ（Auth.js のセッションの確認は、テストでは置き換えてある）。
 *
 * @param url  要求の URL
 * @param init 要求のヘッダーなど
 */
async function runProxy(url: string, init?: ConstructorParameters<typeof NextRequest>[1]): Promise<Response> {
  // auth(処理) の型は、ルートハンドラーの形（第 2 引数に params）になる。置き換えた auth は処理をそのまま返すので、第 2 引数は使われない
  const response = await proxy(new NextRequest(url, init), { params: Promise.resolve({}) })
  if (!response) {
    throw new Error('proxy が応答を返さなかった')
  }
  return response
}

describe('要求ごとの確認（proxy）', () => {
  test('いまの画面のパスとクエリを、要求のヘッダーで画面に知らせる（ログインの後にその画面へ戻すため）', async () => {
    const response = await runProxy('http://localhost:3000/products?category=abc&page=2')

    // NextResponse.next の要求のヘッダーの上書きは、x-middleware-request-<名前> で次の処理に渡る
    expect(response.headers.get('x-middleware-request-x-pathname')).toBe('/products?category=abc&page=2')
  })

  test('要求ごとに追跡の ID（traceparent）を作り、要求のヘッダーで画面に知らせる', async () => {
    const first = await runProxy('http://localhost:3000/products')
    const second = await runProxy('http://localhost:3000/products')

    const traceparent = first.headers.get('x-middleware-request-traceparent')
    expect(traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/)
    expect(second.headers.get('x-middleware-request-traceparent')).not.toBe(traceparent)
  })

  test('要求がすでに正しい traceparent を持っていれば、その追跡の ID を引き継ぐ', async () => {
    const incoming = TRACEPARENT

    const response = await runProxy('http://localhost:3000/products', { headers: { traceparent: incoming } })

    expect(response.headers.get('x-middleware-request-traceparent')).toBe(incoming)
  })

  test('Auth.js の口と静的なファイルには、proxy を通さない', () => {
    const matcher = new RegExp(`^${config.matcher[0]}$`)

    expect(matcher.test('/products')).toBe(true)
    expect(matcher.test('/api/auth/callback/keycloak')).toBe(false)
    expect(matcher.test('/_next/static/chunk.js')).toBe(false)
    expect(matcher.test('/favicon.ico')).toBe(false)
  })
})
