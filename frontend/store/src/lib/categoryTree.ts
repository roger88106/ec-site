import type { CategoryNode } from '@/api/types'

/** 深さ（最上位が 1）を付けたカテゴリ */
export type FlatCategory = { id: string; name: string; depth: number }

/** カテゴリの木を、深さ優先で並べた一覧にする（選択肢に使う） */
export function flattenTree(nodes: readonly CategoryNode[], depth = 1): FlatCategory[] {
  return nodes.flatMap((node) => [{ id: node.id, name: node.name, depth }, ...flattenTree(node.children, depth + 1)])
}
