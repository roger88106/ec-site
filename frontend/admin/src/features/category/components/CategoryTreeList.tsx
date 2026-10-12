import { Button } from '@ec-site/shared/ui/button'
import { ArrowDownIcon, ArrowUpIcon } from 'lucide-react'
import type { CategoryNode } from '@/api/types'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { move } from '@/lib/array'
import { MAX_CATEGORY_DEPTH } from '@/lib/categoryTree'

/** 木の各行から呼ぶ操作 */
export type CategoryActions = {
  pending: boolean
  reorder: (parentId: string | null, categoryIds: string[]) => void
  remove: (category: CategoryNode) => void
  rename: (category: CategoryNode) => void
  createChild: (parent: CategoryNode) => void
}

/** 兄弟の一覧。子があれば入れ子で表示する */
export function CategoryTreeList({
  nodes,
  parentId,
  actions,
}: {
  nodes: CategoryNode[]
  parentId: string | null
  actions: CategoryActions
}) {
  const ids = nodes.map((node) => node.id)
  return (
    <ul className={parentId === null ? 'space-y-1' : 'mt-1 space-y-1 border-l pl-6'}>
      {nodes.map((node, index) => (
        <li key={node.id}>
          <div className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-muted">
            <span className="flex-1">{node.name}</span>
            <Button
              aria-label={`${node.name}を上へ`}
              disabled={index === 0 || actions.pending}
              onClick={() => actions.reorder(parentId, move(ids, index, index - 1))}
              size="icon-xs"
              variant="ghost"
            >
              <ArrowUpIcon />
            </Button>
            <Button
              aria-label={`${node.name}を下へ`}
              disabled={index === nodes.length - 1 || actions.pending}
              onClick={() => actions.reorder(parentId, move(ids, index, index + 1))}
              size="icon-xs"
              variant="ghost"
            >
              <ArrowDownIcon />
            </Button>
            <Button onClick={() => actions.rename(node)} size="xs" variant="outline">
              名称の変更
            </Button>
            {/* 3 階層目には子を作れない（AC-9） */}
            {node.depth < MAX_CATEGORY_DEPTH && (
              <Button onClick={() => actions.createChild(node)} size="xs" variant="outline">
                子を追加
              </Button>
            )}
            <ConfirmDialog
              confirmLabel="削除する"
              description="子カテゴリを持つカテゴリと、商品が所属しているカテゴリは削除できません。"
              onConfirm={() => actions.remove(node)}
              title={`カテゴリ「${node.name}」を削除しますか？`}
              trigger={
                <Button disabled={actions.pending} size="xs" variant="outline">
                  削除
                </Button>
              }
            />
          </div>
          {node.children.length > 0 && <CategoryTreeList actions={actions} nodes={node.children} parentId={node.id} />}
        </li>
      ))}
    </ul>
  )
}
