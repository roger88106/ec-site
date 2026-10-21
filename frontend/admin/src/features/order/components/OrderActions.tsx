import { Button } from '@ec-site/shared/ui/button'
import { useState } from 'react'
import { useTranslations } from 'use-intl'
import type { ApiPathPrefix } from '@/api/invalidate'
import type { OrderDetail } from '@/api/types'
import { useApiMutation } from '@/api/useApiMutation'
import { useMutationGroup } from '@/api/useMutationGroup'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorMessage } from '@/components/ErrorMessage'
import { CancelOrderDialog } from '@/features/order/components/CancelOrderDialog'

/**
 * 注文の操作。申請の承認・却下と、運営者の判断によるキャンセル。<br />
 * どれも確認してから送る。キャンセルで在庫数と調整履歴も変わるので、成功したら在庫も取り直す。
 * 行える操作がない（キャンセル済み）ときは何も出さない。
 */
export function OrderActions({ order }: { order: OrderDetail }) {
  const t = useTranslations('order.actions')
  const [cancelling, setCancelling] = useState(false)
  const path = { params: { path: { orderId: order.id } } }
  const invalidate: ApiPathPrefix[] = ['/orders', '/inventory']
  const approve = useApiMutation('put', '/orders/{orderId}/cancellation-request/approve', {
    success: t('approved'), // 承認の知らせ
    invalidate,
  })
  const reject = useApiMutation('put', '/orders/{orderId}/cancellation-request/reject', {
    success: t('rejected'), // 却下の知らせ
    invalidate,
  })
  const cancel = useApiMutation('put', '/orders/{orderId}/cancel', {
    success: t('cancelled') /* キャンセルの知らせ */,
    invalidate,
  })
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
            confirmLabel={t('approveConfirm') /* 承認してキャンセル */}
            description={t('approveDescription') /* 承認の影響 */}
            onConfirm={() => approve.mutate(path)}
            title={t('approveTitle', { orderNumber: order.orderNumber }) /* 承認の確認 */}
            trigger={<Button disabled={pending}>{t('approve') /* 申請を承認する */}</Button>}
          />
        )}
        {actions.rejectCancellation && (
          <ConfirmDialog
            confirmLabel={t('rejectConfirm') /* 却下する */}
            description={t('rejectDescription') /* 却下の影響 */}
            onConfirm={() => reject.mutate(path)}
            title={t('rejectTitle', { orderNumber: order.orderNumber }) /* 却下の確認 */}
            trigger={
              <Button disabled={pending} variant="outline">
                {t('reject') /* 申請を却下する */}
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
            {t('cancel') /* キャンセルする */}
          </Button>
        )}
      </div>
      <ErrorMessage error={error} />
      {cancelling && (
        <CancelOrderDialog
          askRestock={order.actions.cancelAsksRestock}
          error={cancel.error}
          onClose={() => setCancelling(false)}
          onSubmit={(reason, restock, callbacks) => cancel.mutate({ ...path, body: { reason, restock } }, callbacks)}
          orderNumber={order.orderNumber}
          pending={cancel.isPending}
        />
      )}
    </div>
  )
}
