import { setupServer } from 'msw/node'
import { createOpenApiHttp, type HttpMethod, type ResponseResolver, type ResponseResolverInfo } from 'openapi-msw'
import type { paths } from '@/api/generated/admin'
import type { ErrorCode, Problem } from '@/api/types'

/**
 * 管理 API の応答を差し替えるハンドラーを作る（管理画面の構成 第 9 節）。<br />
 * パス・パラメーター・リクエストとレスポンスの本文は admin.yaml の型に合わせる。admin.yaml にない応答は型エラーになる。
 * 例：`http.get('/tax-categories', ({ response }) => response(200).json({ items: [] }))`
 */
export const http = createOpenApiHttp<paths>({ baseUrl: `${window.location.origin}/api/admin` })

/**
 * 結合テストで API の代わりに応答するサーバー。<br />
 * 既定のハンドラーは持たない。各テストで `server.use(...)` に、そのテストで呼ばれる API の応答を渡す。
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
 * @returns Problem の本文
 */
export function problem(status: number, errorCode: ErrorCode, message: string, errors?: Problem['errors']): Problem {
  return { type: 'about:blank', status, errorCode, message, traceId: 'test-trace', errors }
}

/** 更新の競合（409、CONCURRENT_MODIFICATION）の本文 */
export function concurrentModification(): Problem {
  return problem(409, 'CONCURRENT_MODIFICATION', 'バージョンが一致しません。')
}

/**
 * 更新の競合（409）の応答を作る。ハンドラーに渡される `response` を渡す。<br />
 * 409 が契約にない API では、型エラーになる。
 * 例：`http.put('/products/{productId}', ({ response }) => conflict(response))`
 *
 * @param response ハンドラーに渡される `response`
 * @returns 409 の応答
 */
export function conflict<Result>(
  response: (status: 409) => { json: (body: Problem, init?: undefined) => Result },
): Result {
  return response(409).json(concurrentModification())
}

/**
 * 条件で応答を変える関数の型。第 2 引数の `response` で、契約に合った応答を作る（`Response` を返さない）。<br />
 * 例：`reply: Reply<'/tax-categories', 'post', CreateTaxCategoryRequest>`、
 * `(body, response) => response(409).json(problem(...))`
 *
 * @typeParam Path admin.yaml のパス
 * @typeParam Method HTTP のメソッド（小文字）
 * @typeParam Arg 関数に渡す値（リクエストの本文など）
 */
export type Reply<Path extends keyof paths, Method extends HttpMethod, Arg> = (
  arg: Arg,
  response: ResponseResolverInfo<paths, Path, Method>['response'],
) => ReturnType<ResponseResolver<paths, Path, Method>>
