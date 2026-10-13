'use client'

import { Button } from '@ec-site/shared/ui/button'
import { Label } from '@ec-site/shared/ui/label'
import { useTranslations } from 'next-intl'
import { useActionState } from 'react'
import { requestCancellation } from '@/actions/order'
import { schemas } from '@/api/generated/constraints'

/** 理由の長さの上限 */
const REASON_MAX_LENGTH = schemas.RequestOrderCancellationRequest.reason.maxLength

/**
 * キャンセルの申請。理由は任意。<br />
 * 申請は 1 つの注文に 1 回だけなので、送る前に確かめる。
 */
export function CancellationRequestForm({ orderId }: { orderId: string }) {
  const t = useTranslations('order.requestForm')
  const [result, formAction, pending] = useActionState(requestCancellation, null)
  return (
    <form
      action={formAction}
      className="space-y-3"
      onSubmit={(event) => {
        if (!window.confirm(t('confirm') /* 申請の確認 */)) {
          event.preventDefault()
        }
      }}
    >
      <input name="orderId" type="hidden" value={orderId} />
      <div className="space-y-1.5">
        <Label htmlFor="cancellation-reason">{t('reason') /* 理由 */}</Label>
        <textarea
          className="min-h-20 w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          id="cancellation-reason"
          maxLength={REASON_MAX_LENGTH}
          name="reason"
        />
        <p className="text-muted-foreground text-xs">{t('maxLength', { max: REASON_MAX_LENGTH }) /* 長さの上限 */}</p>
      </div>
      <Button disabled={pending} type="submit" variant="outline">
        {pending ? t('requesting') : t('submit') /* 申請中か、申請する */}
      </Button>
      {result?.ok === false && (
        <p aria-live="polite" className="text-destructive text-sm">
          {result.message}
        </p>
      )}
    </form>
  )
}
