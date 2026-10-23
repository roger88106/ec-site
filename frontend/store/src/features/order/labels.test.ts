import { describe, expect, test } from 'vitest'
import { orderStatusBadge } from '@/features/order/labels'

describe('出荷後の状態（AC-179、180）', () => {
  test('AC-179、180 出荷後は、注文状態の代わりに配送状態を示す', () => {
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
