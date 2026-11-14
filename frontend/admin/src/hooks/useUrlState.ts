import { useSearchParams } from 'react-router'

/**
 * 画面の状態を URL のクエリに持つ（管理画面の構成の「画面の URL」）。
 *
 * URL が状態の正本なので、戻る・再読み込み・共有しても同じ状態になる。
 *
 * @param parse     URL のクエリから状態を読む
 * @param serialize 状態を URL のクエリにする
 * @returns 今の状態、状態を URL に反映する関数、入力欄を再作成するための key（URL が変わると変わる）
 */
export function useUrlState<T>(parse: (params: URLSearchParams) => T, serialize: (value: T) => URLSearchParams) {
  const [searchParams, setSearchParams] = useSearchParams()
  return {
    value: parse(searchParams),
    apply: (next: T) => setSearchParams(serialize(next)),
    key: searchParams.toString(),
  }
}
