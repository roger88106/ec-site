import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ProductSummary } from '@/api/types'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

/** 一覧に出す商品（公開中） */
const SHIRT: ProductSummary = {
  id: '00000000-0000-0000-0000-000000000101',
  productCode: 'ABC-001',
  name: 'T シャツ',
  status: 'PUBLISHED',
  variantCount: 2,
}

/** 一覧に出す商品（非公開） */
const CAP: ProductSummary = {
  id: '00000000-0000-0000-0000-000000000102',
  productCode: 'ABC-002',
  name: '帽子',
  status: 'UNPUBLISHED',
  variantCount: 1,
}

/**
 * 商品の一覧の画面が使う API に応答する。一覧は 2 ページあるものとし、どの条件でも items を返す。<br />
 * 一覧と在庫の集計の要求のクエリは、それぞれ listQueries、summaryQueries に取っておく。
 */
function serveList(items: ProductSummary[], summary: (productIds: string) => Response) {
  const state = { listQueries: [] as string[], summaryQueries: [] as string[] }
  server.use(
    http.get('/categories', ({ response }) => response(200).json({ items: [] })),
    http.get('/products', ({ request, response }) => {
      state.listQueries.push(new URL(request.url).search)
      return response(200).json({ items, page: 1, size: 20, totalItems: 25, totalPages: 2 })
    }),
    http.get('/inventory/stock-summaries', ({ request }) => {
      const productIds = new URL(request.url).searchParams.get('productIds') ?? ''
      state.summaryQueries.push(productIds)
      return summary(productIds) as never
    }),
  )
  return state
}

/** 商品品番の行の、在庫数の合計の欄 */
function totalOf(productCode: string): HTMLElement {
  const row = screen.getByRole('link', { name: productCode }).closest('tr')
  if (!row) {
    throw new Error(`${productCode} の行がない`)
  }
  return within(row).getAllByRole('cell')[4]
}

describe('商品の一覧', () => {
  it('AC-49 表示中のページの商品の在庫数の合計を、集計の API から取って組み合わせる', async () => {
    const state = serveList([SHIRT, CAP], () =>
      Response.json({
        items: [
          { productId: CAP.id, totalQuantity: 0 },
          { productId: SHIRT.id, totalQuantity: 12 },
        ],
      }),
    )
    renderApp('/products')

    expect(await screen.findByRole('cell', { name: 'T シャツ' })).toBeInTheDocument()
    expect(await within(totalOf('ABC-001')).findByText('12')).toBeInTheDocument()
    expect(totalOf('ABC-002')).toHaveTextContent('0')
    expect(state.summaryQueries).toEqual([`${SHIRT.id},${CAP.id}`])
  })

  it('AC-49 在庫の集計の取得に失敗しても、一覧は出し、在庫数の合計は取得できないと出す', async () => {
    // 4xx にする。5xx は取得を繰り返すので、失敗を表示するまでに時間がかかる
    serveList([SHIRT], () => Response.json(problem(400, 'INVALID_REQUEST', '入力に誤りがあります。'), { status: 400 }))
    renderApp('/products')

    expect(await screen.findByRole('cell', { name: 'T シャツ' })).toBeInTheDocument()
    expect(await within(totalOf('ABC-001')).findByText('取得できません')).toBeInTheDocument()
  })

  it('AC-48 絞り込みは URL のクエリになり、1 ページ目から API に送る', async () => {
    const state = serveList([SHIRT], () => Response.json({ items: [] }))
    const { user, router } = renderApp('/products?page=2')
    await screen.findByRole('cell', { name: 'T シャツ' })

    await user.type(screen.getByLabelText('品番（前方一致）'), 'abc')
    await user.type(screen.getByLabelText('商品名（部分一致）'), ' シャツ ')
    await user.click(screen.getByRole('combobox', { name: '状態' }))
    await user.click(await screen.findByRole('option', { name: '公開' }))
    await user.click(screen.getByRole('button', { name: '絞り込む' }))

    const expected = `?code=ABC&name=${encodeURIComponent('シャツ')}&status=PUBLISHED`
    expect(new URLSearchParams(router.state.location.search).toString()).toBe(expected.slice(1))
    // 取得し直す間は前の一覧を出したままなので、要求が届くのを待つ
    await waitFor(() => expect(state.listQueries.at(-1)).toBe(`${expected}&page=1`))
  })

  it('AC-48 URL のクエリの絞り込みとページで一覧を取得し、入力欄にも出す（戻る・再読み込みで同じ一覧）', async () => {
    const state = serveList([SHIRT], () => Response.json({ items: [] }))
    const { user, router } = renderApp('/products?code=ABC&status=PUBLISHED&page=2')

    await screen.findByRole('cell', { name: 'T シャツ' })
    expect(state.listQueries).toEqual(['?code=ABC&status=PUBLISHED&page=2'])
    expect(screen.getByLabelText('品番（前方一致）')).toHaveValue('ABC')
    expect(screen.getByRole('combobox', { name: '状態' })).toHaveTextContent('公開')

    await user.click(screen.getByRole('button', { name: 'クリア' }))
    expect(router.state.location.search).toBe('')
    expect(await screen.findByLabelText('品番（前方一致）')).toHaveValue('')
  })
})
