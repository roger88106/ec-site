import { describe, expect, test } from 'vitest'
import { CancellationSection } from '@/features/order/components/CancellationSection'
import { order } from '@/test/fixtures'
import { renderMarkup } from '@/test/render'

/** 申請も支払もできない（API のフラグ） */
const NO_ACTIONS = { requestCancellation: false, pay: false }

describe('注文のキャンセルの欄', () => {
  test('AC-155 支払期限切れでキャンセルされた注文は、そのことを示す', () => {
    const html = renderMarkup(
      <CancellationSection
        contactEmail="contact@shop.example"
        order={order({
          status: 'CANCELLED',
          actions: NO_ACTIONS,
          cancellation: { cancelledAt: '2026-10-10T15:35:00Z', kind: 'PAYMENT_EXPIRED' },
        })}
      />,
    )

    expect(html).toContain('お支払い期限までにお支払いがなかったため')
  })

  test('欄を出さない状況（支払待ち）の注文で描くと、使い方の誤りとして例外にする', () => {
    expect(() =>
      renderMarkup(
        <CancellationSection
          contactEmail="contact@shop.example"
          order={order({ status: 'PENDING_PAYMENT', actions: NO_ACTIONS })}
        />,
      ),
    ).toThrow('キャンセルの欄を出さない状況で描かれた：PENDING_PAYMENT')
  })
})
