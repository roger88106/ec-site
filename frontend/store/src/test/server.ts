import { HttpResponse, http as mswHttp } from 'msw'
import { setupServer } from 'msw/node'
import { createOpenApiHttp } from 'openapi-msw'
import type { paths } from '@/api/generated/store'
import type { ErrorCode, Problem } from '@/api/types'

/** テストで呼ぶ店舗 API の接続先（client.ts の既定） */
export const STORE_API = 'http://localhost:8080/api/store'

/** テストで呼ぶ IdP（消費者の realm。auth.ts の既定） */
export const IDP = 'http://localhost:8180/realms/ec-store'

/**
 * 店舗 API の応答を差し替えるハンドラーを作る（店舗画面の構成 第 7 節）。<br />
 * パス・パラメーター・リクエストとレスポンスの本文は store.yaml の型に合わせる。store.yaml にない応答は型エラーになる。
 * 例：`http.post('/carts', ({ response }) => response(201).json({ id: CART_ID }))`
 */
export const http = createOpenApiHttp<paths>({ baseUrl: STORE_API })

/**
 * サーバーの処理のテストで、店舗 API と IdP の代わりに応答するサーバー。<br />
 * 既定のハンドラーは持たない。各テストで `server.use(...)` に、そのテストで呼ばれる API の応答を渡す。
 * ハンドラーのない API が呼ばれたら、テストを失敗させる（setup.ts）。
 */
export const server = setupServer()

/**
 * 要求を通信の失敗（接続できない）にするハンドラーを作る。
 *
 * @param method HTTP のメソッド
 * @param url    完全な URL。`:name` でパスのパラメーターを書ける（例：`${STORE_API}/carts/:cartId/items`）
 * @returns 通信の失敗にするハンドラー
 */
export function networkError(method: 'get' | 'post' | 'put' | 'patch' | 'delete', url: string) {
  // 通信の失敗は store.yaml にない応答なので、型付きの http でなく、型のない msw のハンドラーで作る
  return mswHttp[method](url, () => HttpResponse.error())
}

/**
 * エラーレスポンス（Problem）の本文を作る。画面に出るのは message。
 *
 * @param status    HTTP の状態
 * @param errorCode store.yaml の errorCode（例：INSUFFICIENT_STOCK）
 * @param message   画面にそのまま出すメッセージ
 * @param errors    入力検証エラーの項目ごとのメッセージ
 * @returns Problem の本文
 */
export function problem(status: number, errorCode: ErrorCode, message: string, errors?: Problem['errors']): Problem {
  return { type: 'about:blank', status, errorCode, message, traceId: 'test-trace', errors }
}
