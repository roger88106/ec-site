import { useState } from 'react'
import { toast } from 'sonner'
import { $api } from '@/api/client'
import { useInvalidate } from '@/api/invalidate'
import type { OrderDetail } from '@/api/types'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import { Button } from '@/components/ui/button'
import { CancelOrderDialog } from '@/features/order/components/CancelOrderDialog'
import { availableActions } from '@/features/order/order'

/**
 * 注文の操作（AC-101〜103、105）。申請の承認・却下と、運営者の判断によるキャンセル。<br />
 * どれも確認してから送る。キャンセルで在庫数と調整履歴も変わるので、成功したら在庫も取り直す。
 * 行える操作がない（キャンセル済み）ときは何も出さない。
 */
export function OrderActions({ order }: { order: OrderDetail }) {
  const invalidate = useInvalidate()
  const [cancelling, setCancelling] = useState(false)
  const path = { params: { path: { orderId: order.id } } }
  /** 操作が成功したあとの処理 */
  const onChanged = (message: string) => async () => {
    toast.success(message)
    await invalidate('/orders', '/inventory')
  }
  const approve = $api.useMutation('put', '/orders/{orderId}/cancellation-request/approve', {
    onSuccess: onChanged('申請を承認し、キャンセルしました'),
  })
  const reject = $api.useMutation('put', '/orders/{orderId}/cancellation-request/reject', {
    onSuccess: onChanged('申請を却下しました'),
  })
  const cancel = $api.useMutation('put', '/orders/{orderId}/cancel', { onSuccess: onChanged('キャンセルしました') })
  const actions = availableActions(order.status)
  const pending = approve.isPending || reject.isPending || cancel.isPending
  // キャンセルの失敗はダイアログの中に出す
  const error = approve.error ?? reject.error

  if (!actions.approve && !actions.reject && !actions.cancel) {
    return null
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {actions.approve && (
          <ConfirmDialog
            confirmLabel="承認してキャンセルする"
            description="注文をキャンセルし、明細の数量だけ在庫数を戻します。元に戻せません。"
            onConfirm={() => approve.mutate(path)}
            title={`注文 ${order.orderNumber} のキャンセルの申請を承認しますか？`}
            trigger={<Button disabled={pending}>申請を承認する</Button>}
          />
        )}
        {actions.reject && (
          <ConfirmDialog
            confirmLabel="却下する"
            description="注文は「受付」に戻ります。お客様は、もう一度申請できません。"
            onConfirm={() => reject.mutate(path)}
            title={`注文 ${order.orderNumber} のキャンセルの申請を却下しますか？`}
            trigger={
              <Button disabled={pending} variant="outline">
                申請を却下する
              </Button>
            }
          />
        )}
        {actions.cancel && (
          <Button
            disabled={pending}
            onClick={() => {
              cancel.reset()
              setCancelling(true)
            }}
            variant="destructive"
          >
            キャンセルする
          </Button>
        )}
      </div>
      <ErrorMessage error={error} />
      {cancelling && (
        <CancelOrderDialog
          error={cancel.error}
          onClose={() => setCancelling(false)}
          onSubmit={(reason, callbacks) => cancel.mutate({ ...path, body: { reason } }, callbacks)}
          orderNumber={order.orderNumber}
          pending={cancel.isPending}
        />
      )}
    </div>
  )
}
