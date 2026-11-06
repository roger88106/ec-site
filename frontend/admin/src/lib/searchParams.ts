/**
 * URL のクエリのページ番号を読む。1 以上の整数だけをページ番号として扱い、それ以外（なし、形の違う値）は 1 ページ目にする。
 *
 * @param params 画面のクエリ
 */
export function pageParam(params: URLSearchParams): number {
  const page = Number(params.get('page'))
  return Number.isInteger(page) && page >= 1 ? page : 1
}

/**
 * URL のクエリの値を、列挙の値として読む。列挙にない値（なし、形の違う値）は既定にする。
 *
 * @param values   列挙の値
 * @param value    クエリの値
 * @param fallback 列挙にない値のときの既定
 */
export function enumParam<T extends string, F>(values: readonly T[], value: string | null, fallback: F): T | F {
  return value !== null && (values as readonly string[]).includes(value) ? (value as T) : fallback
}
