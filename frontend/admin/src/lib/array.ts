/** 一覧の from 番目を to 番目に移した、新しい一覧（並び替えのボタンに使う） */
export function move<T>(items: readonly T[], from: number, to: number): T[] {
  const result = [...items]
  const [item] = result.splice(from, 1)
  result.splice(to, 0, item)
  return result
}
