import { describe, expect, test } from 'vitest'
import ProductListPage from '@/app/products/page'
import { renderStatic } from '@/test/render'
import { http, problem, server } from '@/test/server'

/**
 * 商品一覧の画面を DOM に読み込む。
 *
 * @param searchParams URL のクエリ
 * @returns 商品一覧の画面を読み込んだ DOM
 */
async function renderListPage(searchParams: Record<string, string> = {}) {
  return renderStatic(
    await ProductListPage({ params: Promise.resolve({}), searchParams: Promise.resolve(searchParams) }),
  )
}

describe('商品一覧の画面', () => {
  test('API が入力の誤り（400）を返したら、API のメッセージを出す', async () => {
    server.use(
      http.get('/categories', ({ response }) => response(200).json({ items: [] })),
      http.get('/products', ({ response }) =>
        response(400).json(problem(400, 'INVALID_REQUEST', 'API が決めた条件の誤りの文')),
      ),
    )

    const view = await renderListPage({ minPrice: '-1' })

    expect(view.getByText('API が決めた条件の誤りの文')).toBeInTheDocument()
    expect(view.queryByText('条件に合う商品はありません。')).toBeNull()
  })

  test('条件に合う商品がなければ、該当がないことを示す', async () => {
    server.use(
      http.get('/categories', ({ response }) => response(200).json({ items: [] })),
      http.get('/products', ({ response }) =>
        response(200).json({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 }),
      ),
    )

    const view = await renderListPage()

    expect(view.getByText('条件に合う商品はありません。')).toBeInTheDocument()
  })
})
