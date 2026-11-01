import { QueryCache, QueryClient, type QueryKey } from '@tanstack/react-query'
import { ApiError } from '@/api/errors'

/** 失敗した取得を繰り返す回数（TanStack Query の既定と同じ） */
const MAX_RETRIES = 3

/**
 * 取得ごとの直近の失敗（繰り返しを尽くした末の失敗）。<br />
 * TanStack Query は、データのない取得を取り直すと error を空に戻す。取り直しの間も失敗を示したい画面のために、
 * キャッシュの側で持つ。キーは取得（Query）そのもので、キャッシュごと・取得ごとに分かれ、取得が捨てられれば一緒に消える
 */
const lastErrors = new WeakMap<object, unknown>()

/**
 * 取得の直近の失敗を返す。取り直している間も残り、取得に成功すると消える。まだ失敗していなければ undefined
 *
 * @param queryClient 取得のキャッシュを持つクライアント（createQueryClient で作ったもの）
 * @param queryKey 取得のキー
 */
export function lastQueryError(queryClient: QueryClient, queryKey: QueryKey): unknown {
  const query = queryClient.getQueryCache().find({ queryKey, exact: true })
  return query === undefined ? undefined : lastErrors.get(query)
}

/**
 * サーバー状態のキャッシュを作る（管理画面の構成の「API の呼び出し」）。設定は TanStack Query の既定のまま。<br />
 * ただし 4xx は繰り返しても結果が変わらず、表示が遅れるだけなので繰り返さない。
 * 結合テストでも同じ設定で、テストごとに新しく作る。
 *
 * @param options.retryDelay 繰り返す間隔（ミリ秒）。省けば TanStack Query の既定（1・2・4 秒）。結合テストは 0 にする
 */
export function createQueryClient({ retryDelay }: { retryDelay?: number } = {}) {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => lastErrors.set(query, error),
      onSuccess: (_data, query) => lastErrors.delete(query),
    }),
    defaultOptions: {
      queries: {
        ...(retryDelay === undefined ? {} : { retryDelay }),
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.isClientError) && failureCount < MAX_RETRIES,
      },
    },
  })
}
