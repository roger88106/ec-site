/** Next.js の searchParams（同じ名前が複数あれば配列） */
export type SearchParams = Record<string, string | string[] | undefined>

/** 同じ名前のパラメータが複数あれば、最初のものを使う */
export function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

/** 1 以上の整数だけをページ番号として扱い、それ以外（なし、形の違う値）は 1 ページ目にする */
export function pageParam(value: string | string[] | undefined): number {
  const page = Number(firstParam(value))
  return Number.isInteger(page) && page >= 1 ? page : 1
}
