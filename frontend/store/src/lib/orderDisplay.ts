import { schemas } from '@/api/generated/constraints'
import type { CancellationRequest, OrderStatus, TaxAmount, UnpurchasableReason } from '@/api/types'

/** 金額の書式（3 桁ごとのカンマ） */
const yen = new Intl.NumberFormat('ja-JP')

/** 日時の書式（日本時間の「2026/10/10 14:05」） */
const dateTime = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** 購入できない理由の文言（AC-80）。数量は出さない（AC-64） */
const UNPURCHASABLE_REASON_LABELS: Record<NonNullable<UnpurchasableReason>, string> = {
  NOT_SHOWN: 'お取り扱いを終了しました',
  SALES_SUSPENDED: '販売を停止しています',
  OUT_OF_STOCK: '在庫切れです',
  INSUFFICIENT_STOCK: '在庫が足りません。数量を減らしてください',
}

/** 注文の状態の表示名（BR-9） */
const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  ACCEPTED: '受付',
  CANCELLATION_REQUESTED: 'キャンセル申請中',
  CANCELLED: 'キャンセル',
}

/** 1 品目の数量の選択肢（AC-75。契約の数量の下限から上限まで） */
export const QUANTITY_OPTIONS = Array.from(
  { length: schemas.Quantity.maximum - schemas.Quantity.minimum + 1 },
  (_, index) => schemas.Quantity.minimum + index,
)

/**
 * 注文のキャンセルの状況（AC-95、96）。申請できる、申請中、却下された（問い合わせ先を示す）、キャンセル済み
 */
export type CancellationState = 'REQUESTABLE' | 'PENDING' | 'REJECTED' | 'CANCELLED'

/** 金額（例：「1,000 円」） */
export function formatYen(amount: number): string {
  return `${yen.format(amount)} 円`
}

/** 1 つの税率の消費税額（BR-8。例：「10% 対象 11,000 円（内消費税 1,000 円）」） */
export function taxLabel(tax: TaxAmount): string {
  return `${tax.ratePercent}% 対象 ${formatYen(tax.taxableAmount)}（内消費税 ${formatYen(tax.taxAmount)}）`
}

/** 軸の名前と値（例：「色：黒　サイズ：M」） */
export function axisValuesLabel(axisValues: { name: string; value: string }[]): string {
  return axisValues.map((axis) => `${axis.name}：${axis.value}`).join('　')
}

/** 購入できない理由の文言（AC-80） */
export function unpurchasableReasonLabel(reason: NonNullable<UnpurchasableReason>): string {
  return UNPURCHASABLE_REASON_LABELS[reason]
}

/** 注文の状態の表示名（BR-9） */
export function orderStatusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status]
}

/**
 * 注文のキャンセルの状況（AC-95、96）。申請は 1 つの注文に 1 回だけなので、却下された「受付」の注文は申請できない。
 */
export function cancellationState(order: {
  status: OrderStatus
  cancellationRequest?: Pick<CancellationRequest, 'requestedAt' | 'result'>
}): CancellationState {
  switch (order.status) {
    case 'CANCELLED':
      return 'CANCELLED'
    case 'CANCELLATION_REQUESTED':
      return 'PENDING'
    case 'ACCEPTED':
      return order.cancellationRequest ? 'REJECTED' : 'REQUESTABLE'
  }
}

/** 日時（日本時間の「2026/10/10 14:05」） */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

/** 郵便番号（API はハイフンなしの 7 桁を返す。例：「100-0001」） */
export function formatPostalCode(postalCode: string): string {
  return `${postalCode.slice(0, 3)}-${postalCode.slice(3)}`
}

/**
 * 引当の期限までの残り時間（AC-84。例：「14:59」）。秒の端数は切り捨てる。
 *
 * @param expiresAt 引当の期限
 * @param now       現在時刻
 * @returns 残り時間。期限を過ぎていれば null
 */
export function remainingTime(expiresAt: string, now: Date): string | null {
  const seconds = Math.floor((new Date(expiresAt).getTime() - now.getTime()) / 1000)
  if (seconds <= 0) {
    return null
  }
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
