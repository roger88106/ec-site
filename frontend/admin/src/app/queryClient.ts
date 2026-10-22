import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/api/errors'

/** 失敗した取得を繰り返す回数（TanStack Query の既定と同じ） */
const MAX_RETRIES = 3

/**
 * サーバー状態のキャッシュを作る（管理画面の構成 第 4 節）。設定は TanStack Query の既定のまま。<br />
 * ただし 4xx は繰り返しても結果が変わらず、表示が遅れるだけなので繰り返さない。
 * 結合テストでも同じ設定で、テストごとに新しく作る。
 *
 * @param options.retryDelay 繰り返す間隔（ミリ秒）。省けば TanStack Query の既定（1・2・4 秒）。結合テストは 0 にする
 */
export function createQueryClient({ retryDelay }: { retryDelay?: number } = {}) {
  return new QueryClient({
    defaultOptions: {
      queries: {
        ...(retryDelay === undefined ? {} : { retryDelay }),
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.isClientError) && failureCount < MAX_RETRIES,
      },
    },
  })
}
