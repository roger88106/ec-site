import { formatDateTime } from '@ec-site/shared/lib/format'
import type { Cancellation, CancellationRequest, Order, OrderStatus } from '@/api/types'
import { translate } from '@/i18n/translate'

/**
 * 注文のキャンセルの状況。申請できる、申請中、却下された（問い合わせ先を示す）、キャンセル済み、
 * 支払期限切れでキャンセル済み、まだ申請できない（支払待ち。支払の期限で自動でキャンセルになる）、
 * 出荷済み（もう申請できない）、受付だが申請できず申請の結果もない（キャンセルの欄を出さない）
 */
export type CancellationState =
  | 'REQUESTABLE'
  | 'PENDING'
  | 'REJECTED'
  | 'CANCELLED'
  | 'PAYMENT_EXPIRED'
  | 'NOT_YET'
  | 'SHIPPED'
  | 'UNAVAILABLE'

/**
 * 注文の支払の状況。支払える、支払の確認中（支払の画面から戻った直後）、支払の期限を過ぎた（まもなくキャンセル）、
 * 支払の欄を出さない（支払待ちでない）
 */
export type PaymentState = 'PAYABLE' | 'CONFIRMING' | 'CLOSING' | 'NONE'

/**
 * 注文のキャンセルの状況。申請できるかは API の旗で、却下されたかは申請の結果で決める。
 */
export function cancellationState(order: {
  status: OrderStatus
  actions: Pick<Order['actions'], 'requestCancellation'>
  cancellation?: Cancellation
  cancellationRequest?: CancellationRequest
}): CancellationState {
  if (order.actions.requestCancellation) {
    return 'REQUESTABLE'
  }
  switch (order.status) {
    case 'PENDING_PAYMENT':
      return 'NOT_YET'
    case 'CANCELLED':
      return order.cancellation?.kind === 'PAYMENT_EXPIRED' ? 'PAYMENT_EXPIRED' : 'CANCELLED'
    case 'CANCELLATION_REQUESTED':
      return 'PENDING'
    case 'ACCEPTED':
      return order.cancellationRequest?.result === 'REJECTED' ? 'REJECTED' : 'UNAVAILABLE'
    case 'SHIPPED':
      return 'SHIPPED'
  }
}

/**
 * 注文の支払の状況。支払えるかは API の旗で決める。
 * 支払の画面から戻った直後は、与信の結果がまだ届いていないことがあるので、支払待ちなら「確認中」とする。
 *
 * @param order    注文
 * @param returned 支払の画面から戻った直後か
 */
export function paymentState(
  order: { status: OrderStatus; actions: Pick<Order['actions'], 'pay'> },
  returned: boolean,
): PaymentState {
  if (order.status !== 'PENDING_PAYMENT') {
    return 'NONE'
  }
  if (returned) {
    return 'CONFIRMING'
  }
  return order.actions.pay ? 'PAYABLE' : 'CLOSING'
}

/** 注文の一覧の支払の期限（例：「お支払い期限 2026/10/11 00:35」）。支払待ちでなければ null */
export function paymentDeadlineLabel(order: { status: OrderStatus; paymentDeadline?: string }): string | null {
  if (order.status !== 'PENDING_PAYMENT' || order.paymentDeadline === undefined) {
    return null
  }
  return translate('order.list.paymentDeadline', { deadline: formatDateTime(order.paymentDeadline) })
}
