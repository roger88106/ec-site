import { describe, expect, test } from 'vitest'
import { PaymentSection } from '@/features/order/components/PaymentSection'
import { ORDER_ID } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

/** 支払待ちの注文 */
const pending = {
  id: ORDER_ID,
  status: 'PENDING_PAYMENT' as const,
  paymentDeadline: '2026-10-10T15:35:00Z',
  actions: { requestCancellation: false, pay: true },
}

describe('注文の支払', () => {
  test('AC-151、159 支払待ちなら、支払の期限と「支払う」を示す', () => {
    const html = renderMarkup(<PaymentSection order={pending} returned={false} />)

    expect(html).toContain('2026/10/11 00:35')
    expect(html).toContain('支払う</button>')
  })

  test('AC-150 支払画面から戻った直後で支払待ちなら、「支払の確認中」と示す', () => {
    const html = renderMarkup(<PaymentSection order={pending} returned={true} />)

    expect(html).toContain('お支払いを確認しています')
  })

  test('支払の期限を過ぎたら「支払う」を出さず、まもなくキャンセルになると示す', () => {
    const html = renderMarkup(
      <PaymentSection order={{ ...pending, actions: { requestCancellation: false, pay: false } }} returned={false} />,
    )

    expect(html).not.toContain('支払う</button>')
    expect(html).toContain('まもなくご注文はキャンセルになります')
  })

  test('支払待ちでなければ、何も出さない', () => {
    const html = renderMarkup(
      <PaymentSection
        order={{ id: ORDER_ID, status: 'ACCEPTED', actions: { requestCancellation: true, pay: false } }}
        returned={true}
      />,
    )

    expect(html).toBe('')
  })
})
