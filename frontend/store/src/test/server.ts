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
 * 例：{@code http.post('/carts', ({ response }) => response(201).json({ id: CART_ID }))}
 */
export const http = createOpenApiHttp<paths>({ baseUrl: STORE_API })

/**
 * サーバーの処理のテストで、店舗 API と IdP の代わりに応答するサーバー。<br />
 * 既定のハンドラーは持たない。各テストで {@code server.use(...)} に、そのテストで呼ばれる API の応答を渡す。
 * ハンドラーのない API が呼ばれたら、テストを失敗させる（setup.ts）。
 */
export const server = setupServer()

/**
 * エラーレスポンス（Problem）の本文を作る。画面に出るのは message。
 *
 * @param status    HTTP の状態
 * @param errorCode store.yaml の errorCode（例：INSUFFICIENT_STOCK）
 * @param message   画面にそのまま出すメッセージ
 * @param errors    入力検証エラーの項目ごとのメッセージ
 */
export function problem(status: number, errorCode: ErrorCode, message: string, errors?: Problem['errors']): Problem {
  return { type: 'about:blank', status, errorCode, message, traceId: 'test-trace', errors }
}
