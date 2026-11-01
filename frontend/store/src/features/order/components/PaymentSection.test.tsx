// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { payOrder } from '@/actions/order'
import { PaymentSection } from '@/features/order/components/PaymentSection'
import { order } from '@/test/fixtures'
import { renderInBrowser, renderStatic } from '@/test/render'

// 支払う操作はサーバーアクションを通るので、結果の表示だけを、差し替えた結果で確認する。動作はサーバーアクションのテストと E2E で確認する（テストの書き方の「テストの種類と基準」）
vi.mock('@/actions/order', () => ({ payOrder: vi.fn(async () => ({ ok: true })) }))

/** 支払待ちの注文（支払える） */
const pending = order({
  status: 'PENDING_PAYMENT',
  paymentDeadline: '2026-10-10T15:35:00Z',
  actions: { requestCancellation: false, pay: true },
})

describe('注文の支払', () => {
  test('AC-151、159 支払待ちなら、支払の期限と「支払う」を示す', () => {
    const view = renderStatic(<PaymentSection order={pending} returned={false} />)

    expect(view.getByText(/2026\/10\/11 00:35/)).toBeInTheDocument()
    expect(view.getByRole('button', { name: '支払う' })).toBeInTheDocument()
  })

  test('AC-150 支払画面から戻った直後で支払待ちなら、「支払の確認中」と示す', () => {
    const view = renderStatic(<PaymentSection order={pending} returned={true} />)

    expect(view.getByText(/お支払いを確認しています/)).toBeInTheDocument()
  })

  test('支払待ちでない注文で描くと、使い方の誤りとして例外にする', () => {
    expect(() => renderStatic(<PaymentSection order={order({ status: 'ACCEPTED' })} returned={false} />)).toThrow(
      '支払の欄を出さない状況で描かれた：ACCEPTED',
    )
  })

  test('支払の期限を過ぎたら「支払う」を出さず、まもなくキャンセルになると示す', () => {
    const view = renderStatic(
      <PaymentSection order={{ ...pending, actions: { requestCancellation: false, pay: false } }} returned={false} />,
    )

    expect(view.queryByRole('button', { name: '支払う' })).toBeNull()
    expect(view.getByText(/まもなくご注文はキャンセルになります/)).toBeInTheDocument()
  })

  test('AC-193-1 支払う操作の失敗の文は、送る前からある状態の知らせ（status）の領域に入る', async () => {
    vi.mocked(payOrder).mockResolvedValueOnce({
      ok: false,
      message: '支払画面に移れませんでした。',
      errorCode: 'ORDER_NOT_PAYABLE',
    })
    const { user } = renderInBrowser(<PaymentSection order={pending} returned={false} />)

    // 送る前も、領域は 1 つあり、文はない
    expect(screen.getAllByRole('status')).toHaveLength(1)
    const statusRegion = screen.getByRole('status')
    expect(statusRegion).toBeEmptyDOMElement()

    await user.click(screen.getByRole('button', { name: '支払う' }))

    // 失敗の後は、同じ領域に文が入る
    expect(await screen.findByText('支払画面に移れませんでした。')).toBeInTheDocument()
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toBe(statusRegion)
    expect(statusRegion).toHaveTextContent('支払画面に移れませんでした。')
  })
})
