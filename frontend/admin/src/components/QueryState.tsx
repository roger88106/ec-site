import type { ReactNode } from 'react'
import { ErrorMessage } from '@/components/ErrorMessage'

/** 取得中・取得の失敗を、その取得を使う場所に出す。取得できたら children に渡す */
export function QueryState<T>({
  query,
  children,
}: {
  query: { isPending: boolean; isError: boolean; error: unknown; data: T | undefined }
  children: (data: T) => ReactNode
}) {
  if (query.isPending) {
    return <p className="text-muted-foreground text-sm">読み込み中…</p>
  }
  if (query.isError) {
    return <ErrorMessage error={query.error} />
  }
  return <>{children(query.data as T)}</>
}
