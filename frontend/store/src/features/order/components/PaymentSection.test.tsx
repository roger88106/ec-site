import { describe, expect, test } from 'vitest'
import { PaymentSection } from '@/features/order/components/PaymentSection'
import { order } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

/** 支払待ちの注文（支払える） */
const pending = order({
  status: 'PENDING_PAYMENT',
  paymentDeadline: '2026-10-10T15:35:00Z',
  actions: { requestCancellation: false, pay: true },
})

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

  test('支払待ちでない注文で描くと、使い方の誤りとして例外にする', () => {
    expect(() => renderMarkup(<PaymentSection order={order({ status: 'ACCEPTED' })} returned={false} />)).toThrow(
      '支払の欄を出さない状況で描かれた：ACCEPTED',
    )
  })

  test('支払の期限を過ぎたら「支払う」を出さず、まもなくキャンセルになると示す', () => {
    const html = renderMarkup(
      <PaymentSection order={{ ...pending, actions: { requestCancellation: false, pay: false } }} returned={false} />,
    )

    expect(html).not.toContain('支払う</button>')
    expect(html).toContain('まもなくご注文はキャンセルになります')
  })
})
