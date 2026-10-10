import { useQueryClient } from '@tanstack/react-query'

/** 更新の後に取り直すパスの前方一致（管理画面の構成 第 4 節の表） */
export type ApiPathPrefix = '/tax-categories' | '/categories' | '/products' | '/inventory' | '/orders'

/**
 * 指定したパスで始まる取得を、すべて取り直す関数を返す。<br />
 * openapi-react-query のキャッシュのキーは [メソッド, パス, 引数] なので、パスの前方一致で選ぶ。
 */
export function useInvalidate() {
  const queryClient = useQueryClient()
  return (...prefixes: ApiPathPrefix[]) =>
    queryClient.invalidateQueries({
      predicate: ({ queryKey: [method, path] }) =>
        method === 'get' && typeof path === 'string' && prefixes.some((prefix) => path.startsWith(prefix)),
    })
}
