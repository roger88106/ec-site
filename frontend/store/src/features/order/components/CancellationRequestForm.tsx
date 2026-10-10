'use client'

import { useActionState } from 'react'
import { requestCancellation } from '@/actions/order'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'

/** 理由の長さの上限（AC-95） */
const REASON_MAX_LENGTH = 500

/**
 * キャンセルの申請（AC-95）。理由は任意。<br />
 * 申請は 1 つの注文に 1 回だけ（AC-96）なので、送る前に確かめる。
 */
export function CancellationRequestForm({ orderId }: { orderId: string }) {
  const [result, formAction, pending] = useActionState(requestCancellation, null)
  return (
    <form
      action={formAction}
      className="space-y-3"
      onSubmit={(event) => {
        if (!window.confirm('キャンセルを申請します。申請は 1 回だけできます。よろしいですか？')) {
          event.preventDefault()
        }
      }}
    >
      <input name="orderId" type="hidden" value={orderId} />
      <div className="space-y-1.5">
        <Label htmlFor="cancellation-reason">理由（任意）</Label>
        <textarea
          className="min-h-20 w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          id="cancellation-reason"
          maxLength={REASON_MAX_LENGTH}
          name="reason"
        />
        <p className="text-muted-foreground text-xs">{REASON_MAX_LENGTH} 文字まで</p>
      </div>
      <Button disabled={pending} type="submit" variant="outline">
        {pending ? '申請しています…' : 'キャンセルを申請する'}
      </Button>
      {result?.ok === false && (
        <p aria-live="polite" className="text-destructive text-sm">
          {result.message}
        </p>
      )}
    </form>
  )
}
