import { Button } from '@ec-site/shared/ui/button'
import { useState } from 'react'
import type { ApiPathPrefix } from '@/api/invalidate'
import type { OrderDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import { CancelOrderDialog } from '@/features/order/components/CancelOrderDialog'

/**
 * 注文の操作（AC-101〜103、105）。申請の承認・却下と、運営者の判断によるキャンセル。<br />
 * どれも確認してから送る。キャンセルで在庫数と調整履歴も変わるので、成功したら在庫も取り直す。
 * 行える操作がない（キャンセル済み）ときは何も出さない。
 */
export function OrderActions({ order }: { order: OrderDetail }) {
  const [cancelling, setCancelling] = useState(false)
  const path = { params: { path: { orderId: order.id } } }
  const invalidate: ApiPathPrefix[] = ['/orders', '/inventory']
  const approve = useApiMutation('put', '/orders/{orderId}/cancellation-request/approve', {
    success: '申請を承認し、キャンセルしました',
    invalidate,
  })
  const reject = useApiMutation('put', '/orders/{orderId}/cancellation-request/reject', {
    success: '申請を却下しました',
    invalidate,
  })
  const cancel = useApiMutation('put', '/orders/{orderId}/cancel', { success: 'キャンセルしました', invalidate })
  const { actions } = order
  const { pending } = useMutationGroup(approve, reject, cancel)
  // キャンセルの失敗はダイアログの中に出す
  const { error } = useMutationGroup(approve, reject)

  if (!actions.approveCancellation && !actions.rejectCancellation && !actions.cancel) {
    return null
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {actions.approveCancellation && (
          <ConfirmDialog
            confirmLabel="承認してキャンセルする"
            description="注文をキャンセルし、明細の数量だけ在庫数を戻します。元に戻せません。"
            onConfirm={() => approve.mutate(path)}
            title={`注文 ${order.orderNumber} のキャンセルの申請を承認しますか？`}
            trigger={<Button disabled={pending}>申請を承認する</Button>}
          />
        )}
        {actions.rejectCancellation && (
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
