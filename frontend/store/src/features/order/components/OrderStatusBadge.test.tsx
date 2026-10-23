import { describe, expect, test } from 'vitest'
import { OrderStatusBadge } from '@/features/order/components/OrderStatusBadge'
import { renderMarkup } from '@/test/render'

describe('注文の状態のバッジ', () => {
  test('AC-180 出荷前の注文は、注文の状態を出す', () => {
    expect(renderMarkup(<OrderStatusBadge status="ACCEPTED" />)).toContain('>受付</span>')
  })

  test('AC-179、180 出荷済みの注文は、注文の状態の代わりに配送状態を出す', () => {
    const html = renderMarkup(<OrderStatusBadge shipmentStatus="DELIVERED" status="SHIPPED" />)

    expect(html).toContain('>配達完了</span>')
    expect(html).not.toContain('出荷済み')
  })
})
