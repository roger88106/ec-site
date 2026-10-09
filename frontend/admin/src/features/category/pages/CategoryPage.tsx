import { useState } from 'react'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { useInvalidate } from '@/api/invalidate'
import type { CategoryNode } from '@/api/types'
import { ErrorMessage } from '@/components/ErrorMessage'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { Button } from '@/components/ui/button'
import { CategoryNameDialog } from '@/features/category/components/CategoryNameDialog'
import { type CategoryActions, CategoryTreeList } from '@/features/category/components/CategoryTreeList'

/** 開いているダイアログ。登録は親（null は最上位）、名称の変更は対象のカテゴリを持つ */
type DialogState = { kind: 'create'; parent: CategoryNode | null } | { kind: 'rename'; category: CategoryNode } | null

/** カテゴリの木の表示、登録、名称の変更、並び替え、削除（T707。US-2） */
export function CategoryPage() {
  const query = $api.useQuery('get', '/categories')
  const invalidate = useInvalidate()
  const [dialog, setDialog] = useState<DialogState>(null)
  const onSuccess = () => invalidate('/categories')
  const reorder = $api.useMutation('put', '/categories/order', { onSuccess })
  const remove = $api.useMutation('delete', '/categories/{categoryId}', {
    onSuccess: async () => {
      toast.success('カテゴリを削除しました')
      await onSuccess()
    },
  })

  const actions: CategoryActions = {
    pending: reorder.isPending || remove.isPending,
    reorder: (parentId, categoryIds) => {
      remove.reset()
      reorder.mutate({ body: { parentId, categoryIds } })
    },
    remove: (category) => {
      reorder.reset()
      remove.mutate({ params: { path: { categoryId: category.id } } })
    },
    rename: (category) => setDialog({ kind: 'rename', category }),
    createChild: (parent) => setDialog({ kind: 'create', parent }),
  }

  return (
    <>
      <PageHeader title="カテゴリ">
        <Button onClick={() => setDialog({ kind: 'create', parent: null })}>最上位に追加</Button>
      </PageHeader>
      <div className="max-w-4xl space-y-4">
        <ErrorMessage error={reorder.error ?? remove.error} />
        <QueryState query={query}>
          {({ items }) =>
            items.length === 0 ? (
              <p className="text-muted-foreground text-sm">カテゴリはまだありません。</p>
            ) : (
              <CategoryTreeList actions={actions} nodes={items} parentId={null} />
            )
          }
        </QueryState>
      </div>
      {dialog?.kind === 'create' && <CreateDialog onClose={() => setDialog(null)} parent={dialog.parent} />}
      {dialog?.kind === 'rename' && <RenameDialog category={dialog.category} onClose={() => setDialog(null)} />}
    </>
  )
}

/** カテゴリの登録（AC-8〜10） */
function CreateDialog({ parent, onClose }: { parent: CategoryNode | null; onClose: () => void }) {
  const invalidate = useInvalidate()
  const mutation = $api.useMutation('post', '/categories', {
    onSuccess: async () => {
      toast.success('カテゴリを登録しました')
      await invalidate('/categories')
    },
  })
  return (
    <CategoryNameDialog
      error={mutation.error}
      initialName=""
      onClose={onClose}
      onSubmit={(name, callbacks) => mutation.mutate({ body: { name, parentId: parent?.id ?? null } }, callbacks)}
      pending={mutation.isPending}
      title={parent ? `「${parent.name}」の子カテゴリを追加` : '最上位のカテゴリを追加'}
    />
  )
}

/** カテゴリ名の変更（AC-10、11）。送るバージョンは、ダイアログを開いたときのもの */
function RenameDialog({ category, onClose }: { category: CategoryNode; onClose: () => void }) {
  const invalidate = useInvalidate()
  const mutation = $api.useMutation('put', '/categories/{categoryId}', {
    onSuccess: async () => {
      toast.success('名称を変更しました')
      await invalidate('/categories')
    },
  })
  return (
    <CategoryNameDialog
      error={mutation.error}
      initialName={category.name}
      onClose={onClose}
      onReload={async () => {
        await invalidate('/categories')
        onClose()
      }}
      onSubmit={(name, callbacks) =>
        mutation.mutate(
          { params: { path: { categoryId: category.id } }, body: { name, version: category.version } },
          callbacks,
        )
      }
      pending={mutation.isPending}
      title="名称の変更"
    />
  )
}
