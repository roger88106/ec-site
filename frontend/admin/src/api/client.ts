import createFetchClient, { type Middleware } from 'openapi-fetch'
import createClient from 'openapi-react-query'
import { toApiError } from '@/api/errors'
import type { paths } from '@/api/generated/admin'
import { currentAccessToken, handleUnauthorized } from '@/auth/token'

/** 運営者の access token を Authorization ヘッダーに付ける（管理画面の構成の「ログイン」） */
const withAccessToken: Middleware = {
  onRequest({ request }) {
    const accessToken = currentAccessToken()
    if (accessToken !== undefined) {
      request.headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return request
  },
}

/**
 * エラーの応答を ApiError にして投げる。画面は ApiError だけを扱う（管理画面の構成の「API の呼び出し」）。<br />
 * 401（token が無効になった）なら、ログインし直す。403 は、ほかのエラーと同じく操作した場所に出す。
 */
const throwOnError: Middleware = {
  async onResponse({ response }) {
    if (response.status === 401) {
      handleUnauthorized()
    }
    if (!response.ok) {
      const body = await response
        .clone()
        .json()
        .catch(() => undefined)
      throw toApiError(response.status, body)
    }
    return undefined
  },
}

/**
 * 管理 API（admin.yaml）の fetch クライアント。開発時は Vite のプロキシがバックエンドに転送する。<br />
 * 接続先は画面と同じオリジンの /api/admin。絶対 URL にするのは、結合テスト（Node の fetch）が相対 URL を受け付けないため。
 * 配列のクエリは、admin.yaml の定義（style: form、explode: false）どおりカンマ区切りで送る。
 */
export const fetchClient = createFetchClient<paths>({
  baseUrl: `${window.location.origin}/api/admin`,
  querySerializer: { array: { style: 'form', explode: false } },
})
fetchClient.use(withAccessToken, throwOnError)

/**
 * 管理 API の TanStack Query のクライアント。<br />
 * 取得は {@code $api.useQuery('get', '<パス>', ...)}、更新は {@code useApiMutation('<メソッド>', '<パス>', ...)}（useApiMutation.ts）。
 * 失敗したときの error は、型の上はエラーレスポンスだが、実際は ApiError（または通信の失敗）になる。
 */
export const $api = createClient(fetchClient)
