import { HttpResponse, http as mswHttp } from 'msw'
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

/** 画像のアップロードの署名付き URL（ストレージへの PUT 先） */
export const UPLOAD_URL = 'http://storage.test/images-incoming/upload-1?X-Amz-Signature=test'

/**
 * 画像のアップロードの受付・ストレージへの PUT・登録に応答する（結合テストだけで使う）。
 *
 * 登録すると、商品の画像の最後に加えてバージョンを上げる。送ったバージョンが今のものと違えば 409 にする。
 *
 * @param state `serveProduct` が返した、商品の編集の画面が使う API の状態
 * @returns 受けた呼び出し（「受付 形式 大きさ」「PUT 形式」「登録 version=番号」）
 */
export function serveUpload(state: ProductState) {
  const calls: string[] = []
  server.use(
    http.post('/products/{productId}/image-uploads', async ({ request, response }) => {
      const body = await request.json()
      calls.push(`受付 ${body.contentType} ${body.contentLength}`)
      return response(201).json({
        uploadId: '00000000-0000-0000-0000-000000000901',
        uploadUrl: UPLOAD_URL,
        headers: { 'Content-Type': body.contentType },
        expiresAt: '2026-10-10T10:05:00Z',
      })
    }),
    // ストレージへの送信は admin.yaml にない（署名付き URL への PUT）ので、型のない msw のハンドラーで作る
    mswHttp.put(UPLOAD_URL.split('?')[0], ({ request }) => {
      calls.push(`PUT ${request.headers.get('Content-Type')}`)
      return new HttpResponse(null, { status: 200 })
    }),
    http.post('/products/{productId}/images', async ({ request, response }) => {
      const body = await request.json()
      calls.push(`登録 version=${body.version}`)
      if (body.version !== state.current.version) {
        return conflict(response)
      }
      const id = `00000000-0000-0000-0000-00000000095${state.current.images.length}`
      state.current = {
        ...state.current,
        images: [...state.current.images, { id, url: `http://cdn.test/products/p/${id}.png` }],
        version: state.current.version + 1,
      }
      return response(201).json({ id })
    }),
  )
  return calls
}
