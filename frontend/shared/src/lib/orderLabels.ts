import type messages from '../messages/ja.json'
import { translate } from './translate'

/*
 * 値の型は共有の文言のキーから作る。各画面の生成した列挙の型がこの型に代入できることを型チェックで確認する
 * （列挙に値が増えて文言がなければ型エラーになる）。
 */
/** 注文の状態の値（文言のキー） */
type OrderStatusKey = keyof typeof messages.common.orderStatus
/** 配送状態の値（文言のキー） */
type ShipmentStatusKey = keyof typeof messages.common.shipmentStatus
/** 配送業者の値（文言のキー） */
type CarrierKey = keyof typeof messages.common.carrier

/** 注文の状態の表示名（管理画面と店舗画面で同じ文言） */
export function orderStatusLabel(status: OrderStatusKey): string {
  return translate(`common.orderStatus.${status}`)
}

/** 配送状態の表示名（管理画面と店舗画面で同じ文言） */
export function shipmentStatusLabel(status: ShipmentStatusKey): string {
  return translate(`common.shipmentStatus.${status}`)
}

/** 配送業者の表示名（管理画面と店舗画面で同じ文言） */
export function carrierLabel(carrier: CarrierKey): string {
  return translate(`common.carrier.${carrier}`)
}
