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

/** 理由の長さの上限 */
const REASON_MAX_LENGTH = schemas.CancelOrderRequest.reason.maxLength

/** 入力欄の値。在庫に戻すかは、選ぶまで空（文字列は radio の値） */
type Values = { reason: string; restock: 'true' | 'false' | '' }

/**
 * 運営者の判断によるキャンセルの、理由（必須）を入力するダイアログ。<br />
 * 出荷済み（返送）の注文では、在庫に戻すかも選ばせる（既定を置かない）。送信は onSubmit に任せ、成功したら閉じる。
 */
export function CancelOrderDialog({
  orderNumber,
  askRestock,
  pending,
  error,
  onSubmit,
  onClose,
}: {
  orderNumber: string
  /** 在庫に戻すかを選ばせるか（出荷済みの注文） */
  askRestock: boolean
  pending: boolean
  error: unknown
  onSubmit: (
    reason: string,
    restock: boolean | undefined,
    callbacks: { onSuccess: () => void; onError: (error: unknown) => void },
  ) => void
  onClose: () => void
}) {
  const t = useTranslations('order.cancelDialog')
  const form = useForm<Values>({ defaultValues: { reason: '', restock: '' } })
  return (
    <Dialog onOpenChange={(open) => !open && onClose()} open>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('title', { orderNumber }) /* キャンセルの確認 */}</DialogTitle>
          <DialogDescription>
            {askRestock ? t('returnedDescription') : t('description') /* キャンセルの影響 */}
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4"
          onSubmit={form.handleSubmit(({ reason, restock }) =>
            onSubmit(reason.trim(), askRestock ? restock === 'true' : undefined, {
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
          {askRestock && (
            <fieldset className="grid gap-2">
              <legend className="mb-2 font-medium text-sm">{t('restock') /* 在庫 */}</legend>
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" value="true" {...form.register('restock')} />
                {t('restockYes') /* 在庫に戻す */}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" value="false" {...form.register('restock')} />
                {t('restockNo') /* 在庫に戻さない */}
              </label>
            </fieldset>
          )}
          <ErrorMessage error={error} shownFields={['reason']} />
          <DialogFooter>
            <Button
              disabled={pending || !form.watch('reason').trim() || (askRestock && !form.watch('restock'))}
              type="submit"
              variant="destructive"
            >
              {t('submit') /* キャンセルする */}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
