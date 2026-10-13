import { setupServer } from 'msw/node'
import { createOpenApiHttp } from 'openapi-msw'
import type { paths } from '@/api/generated/admin'
import type { Problem } from '@/api/types'

/**
 * 管理 API の応答を差し替えるハンドラーを作る（管理画面の構成 第 9 節）。<br />
 * パス・パラメーター・リクエストとレスポンスの本文は admin.yaml の型に合わせる。admin.yaml にない応答は型エラーになる。
 * 例：{@code http.get('/tax-categories', ({ response }) => response(200).json({ items: [] }))}
 */
export const http = createOpenApiHttp<paths>({ baseUrl: `${window.location.origin}/api/admin` })

/**
 * 結合テストで API の代わりに応答するサーバー。<br />
 * 既定のハンドラーは持たない。各テストで {@code server.use(...)} に、そのテストで呼ばれる API の応答を渡す。
 * ハンドラーのない API が呼ばれたら、テストを失敗させる（setup.ts）。
 */
export const server = setupServer()

/**
 * エラーレスポンス（Problem）の本文を作る。画面に出るのは message。
 *
 * @param status HTTP の状態
 * @param errorCode admin.yaml の errorCode（例：PRODUCT_CODE_ALREADY_EXISTS）
 * @param message 画面にそのまま出すメッセージ
 * @param errors 入力検証エラーの項目ごとのメッセージ
 */
export function problem(status: number, errorCode: string, message: string, errors?: Problem['errors']): Problem {
  return { type: 'about:blank', status, errorCode, message, traceId: 'test-trace', errors }
}
