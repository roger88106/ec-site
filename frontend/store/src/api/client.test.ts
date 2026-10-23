import { HttpResponse, http as mswHttp } from 'msw'
import { beforeEach, describe, expect, type MockInstance, test, vi } from 'vitest'
import { getApi, getMemberApi, UNCACHED } from '@/api/client'
import { TRACEPARENT_HEADER } from '@/lib/server/trace'
import { ACCESS_TOKEN, signInAs } from '@/test/auth'
import { resetRequest } from '@/test/next'
import { problem, STORE_API, server } from '@/test/server'

/** 要求のヘッダーの traceparent（proxy が付けたもの） */
const TRACEPARENT = '00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01'

describe('店舗 API の口', () => {
  test('会員の口の要求には、いまの要求の traceparent を付ける（バックエンドのログと同じ追跡の ID にする）', async () => {
    resetRequest({ headers: { [TRACEPARENT_HEADER]: TRACEPARENT } })
    let received: string | null = null
    server.use(
      mswHttp.get(`${STORE_API}/me/orders`, ({ request }) => {
        received = request.headers.get(TRACEPARENT_HEADER)
        return HttpResponse.json({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 })
      }),
    )

    await (await getMemberApi()).GET('/me/orders')

    expect(received).toBe(TRACEPARENT)
  })

  test('公開の口の要求にも、いまの要求の traceparent を付ける（キャッシュする GET も同じ）', async () => {
    resetRequest({ headers: { [TRACEPARENT_HEADER]: TRACEPARENT } })
    let received: string | null = null
    server.use(
      mswHttp.get(`${STORE_API}/categories`, ({ request }) => {
        received = request.headers.get(TRACEPARENT_HEADER)
        return HttpResponse.json({ items: [] })
      }),
    )

    await (await getApi()).GET('/categories')

    expect(received).toBe(TRACEPARENT)
  })

  test('会員の口は、呼ぶ側が Authorization を付けた要求には、セッションの token を重ねて付けずそのまま使う', async () => {
    signInAs()
    let received: string | null = null
    server.use(
      mswHttp.get(`${STORE_API}/me/orders`, ({ request }) => {
        received = request.headers.get('Authorization')
        return HttpResponse.json({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 })
      }),
    )

    await (await getMemberApi()).GET('/me/orders', { headers: { Authorization: 'Bearer caller-token' } })

    expect(received).toBe('Bearer caller-token')
  })

  test('UNCACHED を付けて呼ぶと、Next.js のキャッシュを使わずに取り直す（no-store）', async () => {
    server.use(mswHttp.get(`${STORE_API}/categories`, () => HttpResponse.json({ items: [] })))
    const fetchSpy = vi.spyOn(globalThis, 'fetch')

    await (await getApi()).GET('/categories', { ...UNCACHED })

    expect(fetchSpy.mock.calls.map((call) => call[1])).toEqual([expect.objectContaining({ cache: 'no-store' })])
    fetchSpy.mockRestore()
  })

  test('要求に traceparent がなければ、付けない', async () => {
    let received: string | null = 'unset'
    server.use(
      mswHttp.get(`${STORE_API}/categories`, ({ request }) => {
        received = request.headers.get(TRACEPARENT_HEADER)
        return HttpResponse.json({ items: [] })
      }),
    )

    await (await getApi()).GET('/categories')

    expect(received).toBeNull()
  })
})

describe('店舗 API の 5xx のログ', () => {
  let stderr: MockInstance

  beforeEach(() => {
    stderr = vi.spyOn(process.stderr, 'write').mockImplementation(() => true)
  })

  /** 標準エラー出力に書いたログ（1 行 1 件の JSON） */
  function logged(): Record<string, unknown>[] {
    return stderr.mock.calls.map((call) => JSON.parse(String(call[0])))
  }

  test('AC-308 会員の口が 5xx を受けたら、warn のログに状態コード・エラーコード・メソッド・API の定義のパスを出す', async () => {
    signInAs()
    server.use(
      mswHttp.post(`${STORE_API}/orders/:orderId/payment-page`, () =>
        HttpResponse.json(problem(502, 'PAYMENT_GATEWAY_UNAVAILABLE', '決済サービスに接続できませんでした。'), {
          status: 502,
        }),
      ),
    )

    const { error } = await (await getMemberApi()).POST('/orders/{orderId}/payment-page', {
      params: { path: { orderId: 'order-1' } },
    })

    // 呼んだ側は、今どおり応答の本文を読める
    expect(error).toMatchObject({ errorCode: 'PAYMENT_GATEWAY_UNAVAILABLE' })
    expect(logged()).toEqual([
      expect.objectContaining({
        level: 'warn',
        status: 502,
        errorCode: 'PAYMENT_GATEWAY_UNAVAILABLE',
        method: 'POST',
        path: '/orders/{orderId}/payment-page',
      }),
    ])
  })

  test('AC-308 公開の口が 5xx を受けたときも、warn のログに出す', async () => {
    server.use(mswHttp.get(`${STORE_API}/categories`, () => new HttpResponse('Service Unavailable', { status: 503 })))

    await (await getApi()).GET('/categories')

    expect(logged()).toEqual([
      expect.objectContaining({ level: 'warn', status: 503, method: 'GET', path: '/categories' }),
    ])
  })

  test('AC-308 5xx の本文にエラーコードがなければ、エラーコードを null にして出す', async () => {
    server.use(mswHttp.get(`${STORE_API}/categories`, () => HttpResponse.json({ message: 'down' }, { status: 500 })))

    await (await getApi()).GET('/categories')

    expect(logged()).toEqual([expect.objectContaining({ level: 'warn', status: 500, errorCode: null })])
  })

  test('AC-308 4xx では、ログに出さない', async () => {
    server.use(
      mswHttp.get(`${STORE_API}/products/:productId`, () =>
        HttpResponse.json(problem(404, 'NOT_FOUND', '見つかりません。'), { status: 404 }),
      ),
    )

    await (await getApi()).GET('/products/{productId}', { params: { path: { productId: 'product-1' } } })

    expect(stderr).not.toHaveBeenCalled()
  })

  test('AC-309 ログに token、Cookie、要求の本文、クエリを出さない', async () => {
    signInAs()
    server.use(
      mswHttp.post(`${STORE_API}/carts/:cartId/items`, () =>
        HttpResponse.json(problem(500, 'SYSTEM_ERROR', 'システムエラー'), { status: 500 }),
      ),
      mswHttp.get(`${STORE_API}/products/search`, () =>
        HttpResponse.json(problem(500, 'SYSTEM_ERROR', 'システムエラー'), { status: 500 }),
      ),
    )

    await (await getMemberApi()).POST('/carts/{cartId}/items', {
      params: { path: { cartId: 'cart-1' } },
      body: { variantId: 'variant-secret-body', quantity: 1 },
      headers: { Cookie: 'cartId=cookie-secret' },
    })
    await (await getApi()).GET('/products/search', { params: { query: { q: 'query-secret@example.com' } } })

    const lines = stderr.mock.calls.map((call) => String(call[0])).join('')
    expect(logged()).toHaveLength(2)
    for (const secret of [ACCESS_TOKEN, 'cookie-secret', 'variant-secret-body', 'query-secret', 'cart-1']) {
      expect(lines).not.toContain(secret)
    }
    expect(logged().map((entry) => entry.path)).toEqual(['/carts/{cartId}/items', '/products/search'])
  })
})
