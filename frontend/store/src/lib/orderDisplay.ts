import { LOCALE, TIME_ZONE } from '@ec-site/shared/lib/locale'
import { schemas } from '@/api/generated/constraints'
import type { Order, OrderStatus, TaxAmount, UnpurchasableReason } from '@/api/types'
import { translate } from '@/i18n/translate'

/** 金額の書式（3 桁ごとのカンマ） */
const yen = new Intl.NumberFormat(LOCALE)

/** 日時の書式（日本時間の「2026/10/10 14:05」） */
const dateTime = new Intl.DateTimeFormat(LOCALE, {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** 1 品目の数量の選択肢（契約の数量の下限から上限まで） */
export const QUANTITY_OPTIONS = Array.from(
  { length: schemas.Quantity.maximum - schemas.Quantity.minimum + 1 },
  (_, index) => schemas.Quantity.minimum + index,
)

/**
 * 注文のキャンセルの状況。申請できる、申請中、却下された（問い合わせ先を示す）、キャンセル済み
 */
export type CancellationState = 'REQUESTABLE' | 'PENDING' | 'REJECTED' | 'CANCELLED'

/** 金額（例：「1,000 円」） */
export function formatYen(amount: number): string {
  return translate('common.yen', { amount: yen.format(amount) })
}

/** 1 つの税率の消費税額（例：「10% 対象 11,000 円（内消費税 1,000 円）」） */
export function taxLabel(tax: TaxAmount): string {
  return translate('common.taxAmount', {
    ratePercent: tax.ratePercent,
    taxableAmount: formatYen(tax.taxableAmount),
    taxAmount: formatYen(tax.taxAmount),
  })
}

/** 軸の名前と値（例：「色：黒　サイズ：M」） */
export function axisValuesLabel(axisValues: { name: string; value: string }[]): string {
  return axisValues
    .map((axis) => translate('orderDisplay.axisValues.item', axis))
    .join(translate('orderDisplay.axisValues.separator'))
}

/** 購入できない理由の文言。数量は出さない */
export function unpurchasableReasonLabel(reason: NonNullable<UnpurchasableReason>): string {
  return translate(`orderDisplay.unpurchasableReason.${reason}`)
}

/** 注文の状態の表示名 */
export function orderStatusLabel(status: OrderStatus): string {
  return translate(`common.orderStatus.${status}`)
}

/**
 * 注文のキャンセルの状況。申請できるかは API の旗で決める。
 * 申請できない「受付」の注文は、申請が却下された注文（申請は 1 つの注文に 1 回だけ）。
 */
export function cancellationState(order: {
  status: OrderStatus
  actions: Pick<Order['actions'], 'requestCancellation'>
}): CancellationState {
  if (order.actions.requestCancellation) {
    return 'REQUESTABLE'
  }
  switch (order.status) {
    case 'CANCELLED':
      return 'CANCELLED'
    case 'CANCELLATION_REQUESTED':
      return 'PENDING'
    case 'ACCEPTED':
      return 'REJECTED'
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
 * 引当の期限までの残り時間（例：「14:59」）。秒の端数は切り捨てる。
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
