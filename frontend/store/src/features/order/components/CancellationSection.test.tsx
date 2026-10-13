import { describe, expect, test } from 'vitest'
import type { Order } from '@/api/types'
import { CancellationSection } from '@/features/order/components/CancellationSection'
import { ORDER_ID } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

/** 注文（キャンセルの欄が使う項目だけを意味のある値にする） */
const order = (overrides: Partial<Order>): Order => ({
  id: ORDER_ID,
  orderNumber: '20261010-482915',
  orderedAt: '2026-10-10T15:05:00Z',
  status: 'ACCEPTED',
  lines: [],
  amounts: { itemsTotal: 0, shippingFee: 0, total: 0, taxes: [] },
  email: 'member@example.com',
  shippingAddress: {
    name: '山田 太郎',
    postalCode: '1000001',
    prefecture: '東京都',
    address: '千代田区',
    phone: '0312345678',
  },
  actions: { requestCancellation: false, pay: false },
  ...overrides,
})

describe('注文のキャンセルの欄', () => {
  test('AC-155 支払期限切れでキャンセルされた注文は、そのことを示す', () => {
    const html = renderMarkup(
      <CancellationSection
        contactEmail="contact@shop.example"
        order={order({
          status: 'CANCELLED',
          cancellation: { cancelledAt: '2026-10-10T15:35:00Z', kind: 'PAYMENT_EXPIRED' },
        })}
      />,
    )

    expect(html).toContain('お支払い期限までにお支払いがなかったため')
  })

  test('AC-158 支払待ちの注文は、キャンセルの申請を示さない', () => {
    const html = renderMarkup(
      <CancellationSection contactEmail="contact@shop.example" order={order({ status: 'PENDING_PAYMENT' })} />,
    )

    expect(html).toBe('')
  })
})
