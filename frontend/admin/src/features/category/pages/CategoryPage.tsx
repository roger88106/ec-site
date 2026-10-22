import { Button } from '@ec-site/shared/ui/button'
import { useState } from 'react'
import { useTranslations } from 'use-intl'
import { $api } from '@/api/client'
import { schemas } from '@/api/generated/constraints'
import { useInvalidate } from '@/api/invalidate'
import type { CategoryNode } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ErrorMessage } from '@/components/ErrorMessage'
import { PageHeader } from '@/components/PageHeader'
import { QueryState } from '@/components/QueryState'
import { CategoryNameDialog } from '@/features/category/components/CategoryNameDialog'
import { type CategoryActions, CategoryTreeList } from '@/features/category/components/CategoryTreeList'

/** 開いているダイアログ。登録は親（null は最上位）、名称の変更は対象のカテゴリを持つ */
type DialogState = { kind: 'create'; parent: CategoryNode | null } | { kind: 'rename'; category: CategoryNode } | null

/** カテゴリの木の表示、登録、名称の変更、並び替え、削除 */
export function CategoryPage() {
  const t = useTranslations('category.page')
  const query = $api.useQuery('get', '/categories')
  const [dialog, setDialog] = useState<DialogState>(null)
  const reorder = useApiMutation('put', '/categories/order', { invalidate: ['/categories'] })
  const remove = useApiMutation('delete', '/categories/{categoryId}', {
    success: t('deleted'), // 削除の知らせ
    invalidate: ['/categories'],
  })
  const { pending, error, resetErrors } = useMutationGroup(reorder, remove)

  const actions: CategoryActions = {
    pending,
    reorder: (parentId, categoryIds) => {
      resetErrors()
      reorder.mutate({ body: { parentId, categoryIds } })
    },
    remove: (category) => {
      resetErrors()
      remove.mutate({ params: { path: { categoryId: category.id } } })
    },
    rename: (category) => setDialog({ kind: 'rename', category }),
    createChild: (parent) => setDialog({ kind: 'create', parent }),
  }

  return (
    <>
      <PageHeader title={t('title') /* カテゴリ */}>
        <Button onClick={() => setDialog({ kind: 'create', parent: null })}>{t('addTop') /* 最上位に追加 */}</Button>
      </PageHeader>
      <div className="max-w-4xl space-y-4">
        <ErrorMessage error={error} />
        <QueryState query={query}>
          {({ items }) =>
            items.length === 0 ? (
              <p className="text-muted-foreground text-sm">{t('empty') /* まだない */}</p>
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

/** カテゴリの登録 */
function CreateDialog({ parent, onClose }: { parent: CategoryNode | null; onClose: () => void }) {
  const t = useTranslations('category.page')
  const mutation = useApiMutation('post', '/categories', {
    success: t('registered'), // 登録の知らせ
    invalidate: ['/categories'],
  })
  return (
    <CategoryNameDialog
      error={mutation.error}
      initialName=""
      nameMaxLength={schemas.CreateCategoryRequest.name.maxLength}
      onClose={onClose}
      onSubmit={(name, callbacks) => mutation.mutate({ body: { name, parentId: parent?.id ?? null } }, callbacks)}
      pending={mutation.isPending}
      title={parent ? t('createChildTitle', { name: parent.name }) : t('createTopTitle') /* 追加のダイアログの題名 */}
    />
  )
}

/** カテゴリ名の変更。送るバージョンは、ダイアログを開いたときのもの */
function RenameDialog({ category, onClose }: { category: CategoryNode; onClose: () => void }) {
  const t = useTranslations('category.page')
  const invalidate = useInvalidate()
  const mutation = useApiMutation('put', '/categories/{categoryId}', {
    success: t('renamed'), // 変更の知らせ
    invalidate: ['/categories'],
  })
  return (
    <CategoryNameDialog
      error={mutation.error}
      initialName={category.name}
      nameMaxLength={schemas.RenameCategoryRequest.name.maxLength}
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
      title={t('renameTitle') /* 名称の変更 */}
    />
  )
}
