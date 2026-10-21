import { describe, expect, test } from 'vitest'
import { carrierLabel, orderStatusBadge, orderStatusLabel, shipmentStatusLabel } from '@/features/order/labels'

describe('注文の状態（BR-9、14）', () => {
  test('BR-9、14 支払待ち、受付、キャンセル申請中、キャンセル', () => {
    expect(orderStatusLabel('PENDING_PAYMENT')).toBe('支払待ち')
    expect(orderStatusLabel('ACCEPTED')).toBe('受付')
    expect(orderStatusLabel('CANCELLATION_REQUESTED')).toBe('キャンセル申請中')
    expect(orderStatusLabel('CANCELLED')).toBe('キャンセル')
  })
})

describe('出荷後の状態（AC-179、180）', () => {
  test('BR-19 配送状態と配送業者の表示名', () => {
    expect(shipmentStatusLabel('SHIPPED')).toBe('出荷済み')
    expect(shipmentStatusLabel('IN_TRANSIT')).toBe('配送中')
    expect(shipmentStatusLabel('DELIVERED')).toBe('配達完了')
    expect(shipmentStatusLabel('RETURNED')).toBe('返送')
    expect(carrierLabel('YAMATO')).toBe('ヤマト運輸')
    expect(carrierLabel('SAGAWA')).toBe('佐川急便')
    expect(carrierLabel('JAPAN_POST')).toBe('日本郵便')
  })

  test('AC-179 AC-180 出荷後は、注文状態の代わりに配送状態を示す', () => {
    expect(orderStatusBadge({ status: 'SHIPPED', shipmentStatus: 'IN_TRANSIT' }).label).toBe('配送中')
    expect(orderStatusBadge({ status: 'SHIPPED', shipmentStatus: 'DELIVERED' }).label).toBe('配達完了')
  })

  test('出荷前は注文状態を示す', () => {
    expect(orderStatusBadge({ status: 'ACCEPTED' })).toEqual({ label: '受付', variant: 'default' })
  })

  test('返送の後にキャンセルされた注文は、キャンセルを示す', () => {
    expect(orderStatusBadge({ status: 'CANCELLED', shipmentStatus: 'RETURNED' }).label).toBe('キャンセル')
  })
})
