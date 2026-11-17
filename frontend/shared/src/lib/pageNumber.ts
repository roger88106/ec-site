/** 1 以上の整数だけをページ番号として扱い、それ以外（なし、形の違う値）は 1 ページ目にする */
export function toPageNumber(value: string | null | undefined): number {
  const page = Number(value)
  return Number.isInteger(page) && page >= 1 ? page : 1
}
