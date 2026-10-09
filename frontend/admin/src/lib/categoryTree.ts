import type { CategoryNode } from '@/api/types'

/** カテゴリの階層の上限（AC-9） */
export const MAX_CATEGORY_DEPTH = 3

/** カテゴリの木を、深さ優先で並べた一覧にする（選択肢などに使う） */
export function flattenTree(nodes: readonly CategoryNode[]): CategoryNode[] {
  return nodes.flatMap((node) => [node, ...flattenTree(node.children)])
}
