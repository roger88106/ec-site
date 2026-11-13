'use client'

import { Button } from '@ec-site/shared/ui/button'
import { Label } from '@ec-site/shared/ui/label'
import { useTranslations } from 'next-intl'
import { startTransition, useActionState } from 'react'
import { requestCancellation } from '@/actions/order'
import { schemas } from '@/api/generated/constraints'
import { ActionError } from '@/components/ActionError'

/** 理由の長さの上限 */
const REASON_MAX_LENGTH = schemas.RequestOrderCancellationRequest.reason.maxLength

/**
 * キャンセルの申請
 *
 * 理由は任意。申請は 1 つの注文に 1 回だけなので、送る前に確認する。
 */
export function CancellationRequestForm({ orderId }: { orderId: string }) {
  const t = useTranslations('order.requestForm')
  const [result, formAction, pending] = useActionState(requestCancellation, null)
  return (
    <form
      action={formAction}
      className="flex flex-col items-start gap-3"
      // action は JavaScript の読み込み前の送信に使う。読み込み後は onSubmit から送る（action で送ると、React が送った後にフォームを初期値に戻し、失敗したときに理由が消える）
      onSubmit={(event) => {
        event.preventDefault()
        if (window.confirm(t('confirm') /* 申請の確認 */)) {
          const formData = new FormData(event.currentTarget)
          startTransition(() => formAction(formData))
        }
      }}
    >
      <input name="orderId" type="hidden" value={orderId} />
      <div className="w-full space-y-1.5">
        <Label htmlFor="cancellation-reason">{t('reason') /* 理由 */}</Label>
        <textarea
          className="border-input focus-visible:border-ring focus-visible:ring-ring/50 min-h-20 w-full rounded-lg border bg-transparent px-2.5 py-2 text-sm outline-none focus-visible:ring-3"
          id="cancellation-reason"
          maxLength={REASON_MAX_LENGTH}
          name="reason"
        />
        <p className="text-muted-foreground text-xs">{t('maxLength', { max: REASON_MAX_LENGTH }) /* 長さの上限 */}</p>
      </div>
      <Button disabled={pending} type="submit" variant="outline">
        {pending ? t('requesting') : t('submit') /* 申請中か、申請する */}
      </Button>
      <ActionError result={result} />
    </form>
  )
}
