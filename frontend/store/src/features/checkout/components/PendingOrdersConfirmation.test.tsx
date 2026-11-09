// @vitest-environment jsdom
import { screen } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { startCheckout } from '@/actions/cart'
import { payOrder } from '@/actions/order'
import { PendingOrdersConfirmation } from '@/features/checkout/components/PendingOrdersConfirmation'
import { ORDER_ID, OTHER_ORDER_ID, order } from '@/test/fixtures'
import { renderInBrowser } from '@/test/render'

// 支払う操作と開始の操作はサーバーアクションを通るので、送る内容だけを、差し替えた関数で確認する。動作はサーバーアクションのテストと E2E で確認する（テストの書き方の「テストの種類と基準」）
vi.mock('@/actions/cart', () => ({ startCheckout: vi.fn(async () => ({ ok: true })) }))
vi.mock('@/actions/order', () => ({ payOrder: vi.fn(async () => ({ ok: true })) }))

/** 支払待ちの注文（支払える） */
const PAYABLE = order({
  status: 'PENDING_PAYMENT',
  paymentDeadline: '2026-10-10T15:35:00Z',
  actions: { requestCancellation: false, pay: true },
})

/** もう 1 つの支払待ちの注文（期限を過ぎて支払えない） */
const EXPIRED = order({
  id: OTHER_ORDER_ID,
  status: 'PENDING_PAYMENT',
  paymentDeadline: '2026-10-09T15:35:00Z',
  actions: { requestCancellation: false, pay: false },
})

describe('支払待ちの注文の確認の操作', () => {
  test('AC-406-5 「支払へ進む」は、選んだ注文の ID で支払う操作を呼ぶ', async () => {
    const { user } = renderInBrowser(<PendingOrdersConfirmation orders={[PAYABLE, EXPIRED]} />)

    await user.click(screen.getByRole('button', { name: '支払へ進む' }))

    const sent = vi.mocked(payOrder).mock.calls.at(-1)?.[1]
    expect(sent?.get('orderId')).toBe(ORDER_ID)
  })

  test('AC-406-7 「新しく注文する」は、示した全ての注文の ID を送って開始する', async () => {
    const { user } = renderInBrowser(<PendingOrdersConfirmation orders={[PAYABLE, EXPIRED]} />)

    await user.click(screen.getByRole('button', { name: '新しく注文する' }))

    const sent = vi.mocked(startCheckout).mock.calls.at(-1)?.[1]
    expect(sent?.getAll('acknowledgedOrderIds')).toEqual([ORDER_ID, OTHER_ORDER_ID])
  })

  test('AC-411-9、AC-411-10 上限に達しているときは、新しく注文できないことを知らせる文を、読み上げられる役割で示す', () => {
    renderInBrowser(<PendingOrdersConfirmation limitReached orders={[PAYABLE]} />)

    const notice = screen.getByText('お支払い待ちのご注文が上限に達しているため、新しく注文できません。', {
      exact: false,
    })
    expect(notice).toHaveAttribute('role', 'status')
  })

  test('AC-411-9 上限に達していないときは、上限の文を示さない', () => {
    renderInBrowser(<PendingOrdersConfirmation orders={[PAYABLE]} />)

    expect(screen.queryByText(/上限に達しているため/)).toBeNull()
  })

  test('AC-411-11 上限に達しているときも、支払える注文の「支払へ進む」を示す', () => {
    renderInBrowser(<PendingOrdersConfirmation limitReached orders={[PAYABLE, EXPIRED]} />)

    expect(screen.getAllByRole('button', { name: '支払へ進む' })).toHaveLength(1)
  })

  test('AC-406-7 在庫不足などの失敗は、API のメッセージを示す', async () => {
    vi.mocked(startCheckout).mockResolvedValueOnce({
      ok: false,
      message: '在庫が足りない商品があります。',
      errorCode: 'OUT_OF_STOCK',
    })
    const { user } = renderInBrowser(<PendingOrdersConfirmation orders={[PAYABLE]} />)

    await user.click(screen.getByRole('button', { name: '新しく注文する' }))

    expect(await screen.findByText('在庫が足りない商品があります。')).toBeInTheDocument()
  })
})
