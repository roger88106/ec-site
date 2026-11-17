import type { ReactNode } from 'react'
import { useTranslations } from 'use-intl'
import { ApiError } from '@/api/errors'
import { ErrorMessage } from '@/components/ErrorMessage'
import { RetryableError } from '@/components/RetryableError'

/**
 * 取得の状態の表示
 *
 * 取得中・取得の失敗を、その取得を使う場所に出す。取得できたら children に渡す。
 * 取得済みのデータがあるときは、再取得が失敗しても children の画面を残し、その上に取得の失敗と再試行のボタンを表示する（再取得の間はボタンを押せない）。失敗が 4xx のときは、再試行しても結果が変わらないので、ボタンを表示しない。
 * データがないまま失敗したら、全体を取得の失敗にする。
 */
export function QueryState<T>({
  query,
  children,
}: {
  query: {
    isPending: boolean
    isError: boolean
    isFetching: boolean
    error: unknown
    data: T | undefined
    refetch: () => unknown
  }
  children: (data: T) => ReactNode
}) {
  const t = useTranslations('components.queryState')
  if (query.isPending) {
    return <p className="text-muted-foreground text-sm">{t('loading') /* 読み込み中 */}</p>
  }
  if (query.isError && query.data === undefined) {
    return <ErrorMessage error={query.error} />
  }
  return (
    <>
      {query.isError && (
        <RetryableError
          error={query.error}
          fetching={query.isFetching}
          onRetry={() => void query.refetch()}
          retryable={!(query.error instanceof ApiError && query.error.isClientError)}
        />
      )}
      {children(query.data as T)}
    </>
  )
}
