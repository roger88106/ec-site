import { describe, expect, test } from 'vitest'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { renderStatic } from '@/test/render'

describe('注文の状態のバッジ', () => {
  test('AC-180 出荷前の注文は、注文の状態を出す', () => {
    const view = renderStatic(<OrderStatusBadge status="ACCEPTED" />)

    expect(view.getByText('受付')).toBeInTheDocument()
  })

  test('AC-179、180 出荷済みの注文は、注文の状態の代わりに配送状態を出す', () => {
    const view = renderStatic(<OrderStatusBadge shipmentStatus="DELIVERED" status="SHIPPED" />)

    expect(view.getByText('配達完了')).toBeInTheDocument()
    expect(view.queryByText('出荷済み')).toBeNull()
  })
})
