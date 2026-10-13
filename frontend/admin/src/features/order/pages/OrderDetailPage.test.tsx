import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { CancellationRequest, OrderDetail } from '@/api/types'
import { ORDER_ID, orderActions, orderDetail } from '@/test/fixtures'
import { renderApp } from '@/test/renderApp'
import { http, problem, server } from '@/test/server'

/** 注文の詳細の URL */
const URL = `/orders/${ORDER_ID}`

/** 理由つきの、申請中のキャンセルの申請 */
const REQUEST: CancellationRequest = {
  requestedAt: '2026-10-10T02:00:00Z',
  reason: 'サイズを間違えた',
  result: 'PENDING',
}

/** 「キャンセル申請中」の注文 */
const REQUESTED = orderDetail({
  status: 'CANCELLATION_REQUESTED',
  cancellationRequest: REQUEST,
  actions: orderActions({ approveCancellation: true, rejectCancellation: true }),
})

/**
 * 注文の詳細と操作の API に応答する。操作が成功したら、詳細は after を返す。
 * 送ったキャンセルの理由は reasons に取っておく。
 *
 * @param before 操作の前の注文
 * @param after  操作の後の注文
 */
function serveOrder(before: OrderDetail, after: OrderDetail = before) {
  const state = { current: before, reasons: [] as string[] }
  server.use(
    http.get('/orders/{orderId}', ({ response }) => response(200).json(state.current)),
    http.put('/orders/{orderId}/cancellation-request/approve', ({ response }) => {
      state.current = after
      return response(204).empty()
    }),
    http.put('/orders/{orderId}/cancellation-request/reject', ({ response }) => {
      state.current = after
      return response(204).empty()
    }),
    http.put('/orders/{orderId}/cancel', async ({ request, response }) => {
      state.reasons.push((await request.json()).reason)
      state.current = after
      return response(204).empty()
    }),
  )
  return state
}

/** 状態のバッジ（見出しの下の「状態」） */
async function shownStatus(label: string) {
  return screen.findByText(label, { selector: '[data-slot="badge"]' })
}

/** 支払の状態の欄（見出しの下の「支払」） */
async function shownPayment() {
  return (await screen.findByText('支払', { selector: 'dt' })).nextElementSibling
}

describe('注文の詳細', () => {
  it('AC-100 明細（注文時点の写し）、金額の内訳、お届け先、キャンセルの申請を出す', async () => {
    serveOrder(REQUESTED)
    renderApp(URL)

    expect(await screen.findByText('ABC-001-RED')).toBeInTheDocument()
    expect(screen.getByText('色：赤')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '3,300 円' })).toBeInTheDocument()
    expect(screen.getByText('10% 対象 4,300 円（内消費税 390 円）')).toBeInTheDocument()
    expect(screen.getByText('taro@example.com')).toBeInTheDocument()
    expect(screen.getByText(/〒100-0001 東京都/)).toBeInTheDocument()
    expect(screen.getByText('サイズを間違えた')).toBeInTheDocument()
    expect(screen.getByText('申請中')).toBeInTheDocument()
  })

  it('AC-101 申請を承認すると、確認の後で送り、キャンセルになった注文を出す', async () => {
    serveOrder(
      REQUESTED,
      orderDetail({
        status: 'CANCELLED',
        actions: orderActions({ cancel: false }),
        cancellationRequest: {
          ...REQUEST,
          result: 'APPROVED',
          decidedAt: '2026-10-10T03:00:00Z',
          decidedBy: '審査 一郎',
        },
        cancellation: {
          cancelledAt: '2026-10-10T03:00:00Z',
          kind: 'REQUEST_APPROVED',
          reason: null,
          cancelledBy: '審査 一郎',
        },
      }),
    )
    const { user } = renderApp(URL)
    await shownStatus('キャンセル申請中')

    await user.click(screen.getByRole('button', { name: '申請を承認する' }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: '承認してキャンセルする' }))

    expect(await screen.findByText('申請を承認し、キャンセルしました')).toBeInTheDocument()
    expect(await shownStatus('キャンセル')).toBeInTheDocument()
    expect(screen.getByText('お客様の申請の承認')).toBeInTheDocument()
    // AC-142 判断した運営者とキャンセルした運営者の氏名を出す
    expect(screen.getByText('判断した運営者').nextElementSibling).toHaveTextContent('審査 一郎')
    expect(screen.getByText('キャンセルした運営者').nextElementSibling).toHaveTextContent('審査 一郎')
  })

  it('AC-102 申請を却下すると「受付」に戻り、キャンセルの操作だけが残る', async () => {
    serveOrder(REQUESTED, orderDetail({ cancellationRequest: { ...REQUEST, result: 'REJECTED' } }))
    const { user } = renderApp(URL)
    await shownStatus('キャンセル申請中')

    await user.click(screen.getByRole('button', { name: '申請を却下する' }))
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: '却下する' }))

    expect(await screen.findByText('申請を却下しました')).toBeInTheDocument()
    expect(await shownStatus('受付')).toBeInTheDocument()
    expect(screen.getByText('却下')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '申請を承認する' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'キャンセルする' })).toBeInTheDocument()
  })

  it('AC-103 運営者のキャンセルは理由がないと送れず、理由を付けて送ると、キャンセルの内容に理由を出す', async () => {
    const state = serveOrder(
      orderDetail(),
      orderDetail({
        status: 'CANCELLED',
        actions: orderActions({ cancel: false }),
        cancellation: { cancelledAt: '2026-10-10T03:00:00Z', kind: 'OPERATOR', reason: '電話で取り消しの依頼' },
      }),
    )
    const { user } = renderApp(URL)
    await shownStatus('受付')

    await user.click(screen.getByRole('button', { name: 'キャンセルする' }))
    const dialog = await screen.findByRole('dialog')
    const submit = within(dialog).getByRole('button', { name: 'キャンセルする' })
    expect(submit).toBeDisabled()
    await user.type(within(dialog).getByLabelText('理由（必須）'), '電話で取り消しの依頼')
    await user.click(submit)

    expect(await screen.findByText('キャンセルしました')).toBeInTheDocument()
    expect(await shownStatus('キャンセル')).toBeInTheDocument()
    expect(screen.getByText('運営者の判断')).toBeInTheDocument()
    expect(screen.getByText('電話で取り消しの依頼')).toBeInTheDocument()
    expect(state.reasons).toEqual(['電話で取り消しの依頼'])
  })

  it('AC-103 運営者のキャンセルが拒否されたら、ダイアログの中にメッセージを出し、入力を残す', async () => {
    serveOrder(orderDetail())
    server.use(
      http.put(
        '/orders/{orderId}/cancel',
        () =>
          Response.json(
            problem(400, 'INVALID_CANCELLATION_REASON', 'キャンセルの理由は 500 文字以内で入力してください。'),
            {
              status: 400,
            },
          ) as never,
      ),
    )
    const { user } = renderApp(URL)
    await shownStatus('受付')

    await user.click(screen.getByRole('button', { name: 'キャンセルする' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('理由（必須）'), '理由')
    await user.click(within(dialog).getByRole('button', { name: 'キャンセルする' }))

    expect(await within(dialog).findByText('キャンセルの理由は 500 文字以内で入力してください。')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('理由（必須）')).toHaveValue('理由')
  })

  it('承認が拒否されたら（他の人が先に却下した など）、操作の近くにメッセージを出す', async () => {
    serveOrder(REQUESTED)
    server.use(
      http.put(
        '/orders/{orderId}/cancellation-request/approve',
        () =>
          Response.json(
            problem(400, 'CANCELLATION_NOT_PENDING', 'この注文には、承認・却下できるキャンセルの申請がありません。'),
            { status: 400 },
          ) as never,
      ),
    )
    const { user } = renderApp(URL)
    await shownStatus('キャンセル申請中')

    await user.click(screen.getByRole('button', { name: '申請を承認する' }))
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '承認してキャンセルする' }),
    )

    expect(await screen.findByText('この注文には、承認・却下できるキャンセルの申請がありません。')).toBeInTheDocument()
  })

  it('AC-105 キャンセル済みの注文には操作を出さない', async () => {
    serveOrder(
      orderDetail({
        status: 'CANCELLED',
        actions: orderActions({ cancel: false }),
        cancellation: { cancelledAt: '2026-10-10T03:00:00Z', kind: 'OPERATOR', reason: '在庫の誤り' },
      }),
    )
    renderApp(URL)

    expect(await shownStatus('キャンセル')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'キャンセルする' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '申請を承認する' })).not.toBeInTheDocument()
  })
  it('AC-160 支払の状態と、支払の期限を出す', async () => {
    serveOrder(orderDetail({ payment: { status: 'AUTHORIZED', deadline: '2026-10-10T01:30:00Z' } }))
    renderApp(URL)

    expect(await shownPayment()).toHaveTextContent('与信済み')
    expect(screen.getByText('支払の期限').nextElementSibling).toHaveTextContent('2026/10/10 10:30')
  })

  it('AC-165 与信の取消が済むまで「与信の取消待ち」と示す', async () => {
    serveOrder(
      orderDetail({
        status: 'CANCELLED',
        actions: orderActions({ cancel: false }),
        cancellation: { cancelledAt: '2026-10-10T03:00:00Z', kind: 'OPERATOR', reason: '在庫の誤り' },
        payment: { status: 'VOID_PENDING', deadline: '2026-10-10T01:30:00Z' },
      }),
    )
    renderApp(URL)

    expect(await shownPayment()).toHaveTextContent('与信の取消待ち')
  })

  it('AC-155 支払期限切れのキャンセルを示す', async () => {
    serveOrder(
      orderDetail({
        status: 'CANCELLED',
        actions: orderActions({ cancel: false }),
        cancellation: { cancelledAt: '2026-10-10T01:31:00Z', kind: 'PAYMENT_EXPIRED' },
        payment: { status: 'NOT_PAID', deadline: '2026-10-10T01:30:00Z' },
      }),
    )
    renderApp(URL)

    expect(await shownPayment()).toHaveTextContent('支払なし')
    expect(screen.getByText('きっかけ').nextElementSibling).toHaveTextContent('支払期限切れ')
    expect(screen.queryByText('理由')).not.toBeInTheDocument()
  })

  it('AC-158 支払待ちの注文には、キャンセルの操作を出さない', async () => {
    serveOrder(
      orderDetail({
        status: 'PENDING_PAYMENT',
        actions: orderActions({ cancel: false }),
        payment: { status: 'UNPAID', deadline: '2026-10-10T01:30:00Z' },
      }),
    )
    renderApp(URL)

    expect(await shownStatus('支払待ち')).toBeInTheDocument()
    expect(await shownPayment()).toHaveTextContent('未払い')
    expect(screen.queryByRole('button', { name: 'キャンセルする' })).not.toBeInTheDocument()
  })

  it('決済を入れる前の注文には、支払の状態を出さない', async () => {
    serveOrder(orderDetail())
    renderApp(URL)

    expect(await shownStatus('受付')).toBeInTheDocument()
    expect(screen.queryByText('支払')).not.toBeInTheDocument()
  })
})
