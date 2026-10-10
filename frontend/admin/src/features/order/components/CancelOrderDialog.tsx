import { useForm } from 'react-hook-form'
import { setServerFieldErrors } from '@/api/formErrors'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Textarea } from '@/components/ui/textarea'

/** 理由の長さの上限（AC-103） */
const REASON_MAX_LENGTH = 500

/** 入力欄の値 */
type Values = { reason: string }

/**
 * 運営者の判断によるキャンセルの、理由（必須）を入力するダイアログ（AC-103）。<br />
 * 送信は onSubmit に任せ、成功したら閉じる。
 */
export function CancelOrderDialog({
  orderNumber,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  orderNumber: string
  pending: boolean
  error: unknown
  onSubmit: (reason: string, callbacks: { onSuccess: () => void; onError: (error: unknown) => void }) => void
  onClose: () => void
}) {
  const form = useForm<Values>({ defaultValues: { reason: '' } })
  return (
    <Dialog onOpenChange={(open) => !open && onClose()} open>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>注文 {orderNumber} をキャンセルしますか？</DialogTitle>
          <DialogDescription>キャンセルすると、明細の数量だけ在庫数を戻します。元に戻せません。</DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4"
          onSubmit={form.handleSubmit(({ reason }) =>
            onSubmit(reason.trim(), {
              onSuccess: onClose,
              onError: (e) => setServerFieldErrors(e, form.setError, ['reason']),
            }),
          )}
        >
          <Field>
            <FieldLabel htmlFor="cancel-reason">理由（必須）</FieldLabel>
            <Textarea
              autoFocus
              id="cancel-reason"
              maxLength={REASON_MAX_LENGTH}
              {...form.register('reason', { required: true })}
            />
            <FieldError errors={[form.formState.errors.reason]} />
          </Field>
          <ErrorMessage error={error} shownFields={['reason']} />
          <DialogFooter>
            <Button disabled={pending || !form.watch('reason').trim()} type="submit" variant="destructive">
              キャンセルする
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
