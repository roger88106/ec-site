import { withAllOption } from './options'
import { translate } from './translate'

/** カテゴリの木の節（管理 API と店舗 API のカテゴリの木の、共通の形） */
export type CategoryTreeNode = { id: string; name: string; children: readonly CategoryTreeNode[] }

/** 深さ（最上位が 1）を付けたカテゴリ */
export type FlatCategory = { id: string; name: string; depth: number }

/**
 * カテゴリの木を、深さ優先（親の直後に子孫）で並べた一覧にする（選択肢と字下げに使う）。
 *
 * @param nodes カテゴリの木
 * @param depth 最上位の深さ
 * @returns 深さを付けた一覧
 */
export function flattenTree(nodes: readonly CategoryTreeNode[], depth = 1): FlatCategory[] {
  return nodes.flatMap((node) => [{ id: node.id, name: node.name, depth }, ...flattenTree(node.children, depth + 1)])
}

/**
 * カテゴリの選択肢（絞り込み用）。先頭に「すべて」（値は null）を置き、階層は字下げで示す。
 *
 * @param categories 深さを付けたカテゴリの一覧（{@link flattenTree} の結果）
 * @returns 選択肢の値と表示
 */
export function categoryOptions(categories: readonly FlatCategory[]): { value: string | null; label: string }[] {
  return withAllOption(
    categories.map((category) => ({
      value: category.id,
      label: `${translate('common.categoryIndent').repeat(category.depth - 1)}${category.name}`, // 階層の深さだけ字下げ
    })),
  )
}
