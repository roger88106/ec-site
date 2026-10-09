import createFetchClient, { type Middleware } from 'openapi-fetch'
import createClient from 'openapi-react-query'
import { toApiError } from '@/api/errors'
import type { paths } from '@/api/generated/admin'

/** エラーの応答を ApiError にして投げる。画面は ApiError だけを扱う（管理画面の構成 第 4 節） */
const throwOnError: Middleware = {
  async onResponse({ response }) {
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
 * 配列のクエリは、admin.yaml の定義（style: form、explode: false）どおりカンマ区切りで送る。
 */
export const fetchClient = createFetchClient<paths>({
  baseUrl: '/api/admin',
  querySerializer: { array: { style: 'form', explode: false } },
})
fetchClient.use(throwOnError)

/**
 * 管理 API の TanStack Query のクライアント。<br />
 * 取得は {@code $api.useQuery('get', '<パス>', ...)}、更新は {@code $api.useMutation('<メソッド>', '<パス>')}。
 * 失敗したときの error は、型の上はエラーレスポンスだが、実際は ApiError（または通信の失敗）になる。
 */
export const $api = createClient(fetchClient)
