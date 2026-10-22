import type messages from '../messages/ja.json'
import { translate } from './translate'

/*
 * 値の型は共有の文言のキーから作る。各画面の生成した列挙の型がこの型に代入できることを型チェックで確かめる
 * （列挙に値が増えて文言がなければ型エラーになる）。
 */
type OrderStatusKey = keyof typeof messages.common.orderStatus
type ShipmentStatusKey = keyof typeof messages.common.shipmentStatus
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
