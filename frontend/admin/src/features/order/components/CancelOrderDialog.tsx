import { Button } from '@ec-site/shared/ui/button'
import { useForm } from 'react-hook-form'
import { useTranslations } from 'use-intl'
import { setServerFieldErrors } from '@/api/formErrors'
import { schemas } from '@/api/generated/constraints'
import { ErrorMessage } from '@/components/ErrorMessage'
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
const REASON_MAX_LENGTH = schemas.CancelOrderRequest.reason.maxLength

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
  const t = useTranslations('order.cancelDialog')
  const form = useForm<Values>({ defaultValues: { reason: '' } })
  return (
    <Dialog onOpenChange={(open) => !open && onClose()} open>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('title', { orderNumber }) /* キャンセルの確認 */}</DialogTitle>
          <DialogDescription>{t('description') /* キャンセルの影響 */}</DialogDescription>
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
            <FieldLabel htmlFor="cancel-reason">{t('reason') /* 理由 */}</FieldLabel>
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
              {t('submit') /* キャンセルする */}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
