import { describe, expect, test } from 'vitest'
import { carrierLabel, orderStatusLabel, shipmentStatusLabel } from './orderLabels'

describe('orderStatusLabel（BR-9、14、18）', () => {
  test('BR-9、14 支払待ち、受付、キャンセル申請中、キャンセルの状態を表示名にする', () => {
    expect(orderStatusLabel('PENDING_PAYMENT')).toBe('支払待ち')
    expect(orderStatusLabel('ACCEPTED')).toBe('受付')
    expect(orderStatusLabel('CANCELLATION_REQUESTED')).toBe('キャンセル申請中')
    expect(orderStatusLabel('CANCELLED')).toBe('キャンセル')
  })

  test('BR-18 出荷した注文の状態を「出荷済み」と表示する', () => {
    expect(orderStatusLabel('SHIPPED')).toBe('出荷済み')
  })
})

describe('shipmentStatusLabel / carrierLabel', () => {
  test('BR-19 出荷の配送状態を表示名にする', () => {
    expect(shipmentStatusLabel('SHIPPED')).toBe('出荷済み')
    expect(shipmentStatusLabel('IN_TRANSIT')).toBe('配送中')
    expect(shipmentStatusLabel('DELIVERED')).toBe('配達完了')
    expect(shipmentStatusLabel('RETURNED')).toBe('返送')
  })

  test('配送業者のコードを業者名にする', () => {
    expect(carrierLabel('YAMATO')).toBe('ヤマト運輸')
    expect(carrierLabel('SAGAWA')).toBe('佐川急便')
    expect(carrierLabel('JAPAN_POST')).toBe('日本郵便')
  })
})
