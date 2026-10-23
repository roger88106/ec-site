import { translate } from './translate'

/**
 * 選択肢の先頭に「すべて」（値は null。絞り込まない）を付ける。
 *
 * @param options 選択肢の値と表示
 * @returns 先頭に「すべて」を付けた選択肢（元の配列は変えない）
 */
export function withAllOption<V>(
  options: readonly { value: V; label: string }[],
): { value: V | null; label: string }[] {
  return [{ value: null, label: translate('common.all') }, ...options] // すべて
}
