import { Button } from '@ec-site/shared/ui/button'
import { Input } from '@ec-site/shared/ui/input'
import { useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import { schemas } from '@/api/generated/constraints'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'

/** 入力欄の値 */
type Values = { name: string }

/**
 * カテゴリの名称を入力するダイアログ（登録・名称の変更）。<br />
 * 送信は onSubmit に任せ、成功したら閉じる。
 */
export function CategoryNameDialog({
  title,
  initialName,
  pending,
  error,
  onSubmit,
  onClose,
  onReload = onClose,
}: {
  title: string
  initialName: string
  pending: boolean
  error: unknown
  onSubmit: (name: string, callbacks: { onSuccess: () => void; onError: (error: unknown) => void }) => void
  onClose: () => void
  /** バージョン不一致（409）の後の再読み込み。既定は閉じるだけ */
  onReload?: () => void
}) {
  const tCommon = useTranslations('common')
  const form = useForm<Values>({ defaultValues: { name: initialName } })
  return (
    <Dialog onOpenChange={(open) => !open && onClose()} open>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <form
          className="grid gap-4"
          onSubmit={form.handleSubmit(({ name }) =>
            onSubmit(name, {
              onSuccess: onClose,
              onError: (e) => setServerFieldErrors(e, form.setError, ['name']),
            }),
          )}
        >
          <Field>
            <FieldLabel htmlFor="category-name">{tCommon('name') /* 名称 */}</FieldLabel>
            <Input
              autoFocus
              id="category-name"
              maxLength={schemas.CreateCategoryRequest.name.maxLength}
              {...form.register('name', { required: true })}
            />
            <FieldError errors={[form.formState.errors.name]} />
          </Field>
          <ErrorMessage error={error} onReload={onReload} shownFields={['name']} />
          <DialogFooter>
            <Button disabled={pending || !form.watch('name').trim()} type="submit">
              {tCommon('save') /* 保存 */}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
