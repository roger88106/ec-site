import { toPageNumber } from '@ec-site/shared/lib/pageNumber'

/**
 * URL のクエリのページ番号を読む。
 *
 * 1 以上の整数だけをページ番号として扱い、それ以外（なし、形の違う値）は 1 ページ目にする。
 *
 * @param params 画面のクエリ
 * @returns ページ番号
 */
export function pageParam(params: URLSearchParams): number {
  return toPageNumber(params.get('page'))
}

/** URL のクエリに書ける値。空の文字列、null、undefined、false は空として書かない */
type ParamValue = string | number | boolean | null | undefined

/**
 * 値を URL のクエリにする。
 *
 * 空の値と、既定と同じ値は書かない（URL を短く保つ）。true は `true` と書く。
 *
 * @param values   書く値（書く順に並べる）
 * @param defaults 書かない既定の値
 * @returns URL のクエリ
 */
export function writeParams<T extends Record<string, ParamValue>>(
  values: T,
  defaults: Partial<T> = {},
): URLSearchParams {
  const params = new URLSearchParams()
  // クエリの並びは、渡したオブジェクトの項目の順になる
  for (const [name, value] of Object.entries(values)) {
    if (value === undefined || value === null || value === '' || value === false || value === defaults[name]) {
      continue
    }
    params.set(name, String(value))
  }
  return params
}

/**
 * URL のクエリの値を、列挙の値として読む。
 *
 * 列挙にない値（なし、形の違う値）は既定にする。
 *
 * @param values   列挙の値
 * @param value    クエリの値
 * @param fallback 列挙にない値のときの既定
 * @returns 列挙の値。列挙にない値のときは既定
 */
export function enumParam<T extends string, F>(values: readonly T[], value: string | null, fallback: F): T | F {
  return value !== null && (values as readonly string[]).includes(value) ? (value as T) : fallback
}
