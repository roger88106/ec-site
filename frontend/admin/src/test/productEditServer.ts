import type { ProductDetail } from '@/api/types'
import { PRODUCT_ID, productActions, taxCategory } from '@/test/fixtures'
import { conflict, http, server } from '@/test/server'

/** 商品の編集の画面のパス */
export const PAGE_PATH = `/products/${PRODUCT_ID}`

/** 商品の編集の画面が使う API の状態（今の商品と、送ったバージョン、状態の変更・削除のリクエスト） */
type ProductState = { current: ProductDetail; versions: number[]; calls: string[] }

/** 商品の取得と、基本情報の入力欄が使う選択肢（税区分、カテゴリ）に応答する。 */
function serveProductRead(state: ProductState) {
  server.use(
    http.get('/products/{productId}', ({ response }) => response(200).json(state.current)),
    http.get('/tax-categories', ({ response }) => response(200).json({ items: [taxCategory()] })),
    http.get('/categories', ({ response }) => response(200).json({ items: [] })),
  )
}

/** 基本情報の保存に応答する。送ったバージョンが今のものと違えば 409 にし、同じなら保存してバージョンを上げる。 */
function serveProductUpdate(state: ProductState) {
  server.use(
    http.put('/products/{productId}', async ({ request, response }) => {
      const body = await request.json()
      state.versions.push(body.version)
      if (body.version !== state.current.version) {
        return conflict(response)
      }
      state.current = { ...state.current, ...body, version: state.current.version + 1 }
      return response(204).empty()
    }),
  )
}

/** 販売終了と削除に応答し、リクエストを calls に「メソッド パス」で取っておく。 */
function serveProductStatus(state: ProductState) {
  server.use(
    http.put('/products/{productId}/discontinue', ({ response }) => {
      state.calls.push('PUT discontinue')
      state.current = {
        ...state.current,
        status: 'DISCONTINUED',
        actions: productActions({ publish: false, discontinue: false }),
        version: state.current.version + 1,
      }
      return response(204).empty()
    }),
    http.delete('/products/{productId}', ({ query, response }) => {
      state.calls.push(`DELETE version=${query.get('version')}`)
      return response(204).empty()
    }),
    // 削除の後に移る、商品の一覧の画面
    http.get('/products', ({ response }) =>
      response(200).json({ items: [], page: 1, size: 20, totalItems: 0, totalPages: 0 }),
    ),
  )
}

/**
 * 商品の編集の画面が使う API に応答する（結合テストだけで使う）。
 *
 * 商品の取得、基本情報の保存、販売終了・削除の応答を組み合わせる。カードごとの API は、テストで追加する。
 *
 * @param initial 最初の商品
 * @returns 今の商品と、送ったバージョン、状態の変更・削除のリクエスト
 */
export function serveProduct(initial: ProductDetail): ProductState {
  const state: ProductState = { current: initial, versions: [], calls: [] }
  serveProductRead(state)
  serveProductUpdate(state)
  serveProductStatus(state)
  return state
}
