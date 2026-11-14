import { HttpResponse, http as mswHttp } from 'msw'
import { describe, expect, test } from 'vitest'
import ProductDetailPage from '@/app/products/[productId]/page'
import { expectNotFound } from '@/test/next'
import { STORE_API, server } from '@/test/server'

describe('商品の詳細の画面', () => {
  test('形の違う商品 ID は、API を呼ばずに「見つかりません」にする', async () => {
    let called = false
    // どの商品 ID でも呼ばれたことを記録するため、型のない msw のハンドラーで受ける
    server.use(
      mswHttp.get(`${STORE_API}/products/:productId`, () => {
        called = true
        return HttpResponse.json({ message: 'error' }, { status: 404 })
      }),
    )

    await expectNotFound(() =>
      ProductDetailPage({ params: Promise.resolve({ productId: 'not-a-uuid' }), searchParams: Promise.resolve({}) }),
    )

    expect(called).toBe(false)
  })
})
