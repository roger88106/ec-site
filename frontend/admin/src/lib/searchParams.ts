/**
 * URL のクエリのページ番号を読む。1 以上の整数だけをページ番号として扱い、それ以外（なし、形の違う値）は 1 ページ目にする。
 *
 * @param params 画面のクエリ
 */
export function pageParam(params: URLSearchParams): number {
  const page = Number(params.get('page'))
  return Number.isInteger(page) && page >= 1 ? page : 1
}
