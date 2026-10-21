import { describe, expect, test } from 'vitest'
import {
  cancellationRequestResultLabel,
  carrierLabel,
  orderStatusLabel,
  paymentStatusLabel,
  shipmentStatusLabel,
} from '@/features/order/labels'

describe('注文の状態の表示名（BR-9）', () => {
  test('BR-9 受付、キャンセル申請中、キャンセル', () => {
    expect(orderStatusLabel('ACCEPTED')).toBe('受付')
    expect(orderStatusLabel('CANCELLATION_REQUESTED')).toBe('キャンセル申請中')
    expect(orderStatusLabel('CANCELLED')).toBe('キャンセル')
  })

  test('BR-18 出荷済み', () => {
    expect(orderStatusLabel('SHIPPED')).toBe('出荷済み')
  })

  test('AC-100 キャンセルの申請の結果', () => {
    expect(cancellationRequestResultLabel('PENDING')).toBe('申請中')
    expect(cancellationRequestResultLabel('APPROVED')).toBe('承認')
    expect(cancellationRequestResultLabel('REJECTED')).toBe('却下')
  })
})

describe('出荷と支払の表示名', () => {
  test('BR-19 配送状態', () => {
    expect(shipmentStatusLabel('SHIPPED')).toBe('出荷済み')
    expect(shipmentStatusLabel('IN_TRANSIT')).toBe('配送中')
    expect(shipmentStatusLabel('DELIVERED')).toBe('配達完了')
    expect(shipmentStatusLabel('RETURNED')).toBe('返送')
  })

  test('配送業者', () => {
    expect(carrierLabel('YAMATO')).toBe('ヤマト運輸')
    expect(carrierLabel('SAGAWA')).toBe('佐川急便')
    expect(carrierLabel('JAPAN_POST')).toBe('日本郵便')
  })

  test('BR-21 支払状態の追加分', () => {
    expect(paymentStatusLabel('CAPTURED')).toBe('売上確定済み')
    expect(paymentStatusLabel('AUTHORIZATION_EXPIRED')).toBe('与信期限切れ')
    expect(paymentStatusLabel('REFUND_PENDING')).toBe('返金待ち')
    expect(paymentStatusLabel('REFUNDED')).toBe('返金済み')
  })
})
