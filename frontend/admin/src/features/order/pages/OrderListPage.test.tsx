import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { orderSummary } from '@/test/fixtures'
import { findRow, getCell, getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { http, server } from '@/test/server'

/**
 * 注文一覧の API に応答する。受け取ったクエリは queries に取っておく。
 *
 * @param items 返す注文
 */
function serveOrders(items = [orderSummary()]) {
  const queries: URLSearchParams[] = []
  server.use(
    http.get('/orders', ({ request, response }) => {
      queries.push(new URL(request.url).searchParams)
      return response(200).json({ items, page: 1, size: 20, totalItems: items.length, totalPages: 1 })
    }),
  )
  return queries
}

describe('注文一覧', () => {
  test('AC-97 注文番号、注文日時、氏名、総額、状態を出す', async () => {
    serveOrders([
      orderSummary(),
      orderSummary({
        id: '00000000-0000-0000-0000-000000000502',
        orderNumber: '20261009-654321',
        customerName: '佐藤 花子',
        total: 12000,
        status: 'CANCELLATION_REQUESTED',
      }),
    ])
    renderApp('/orders')

    const row = await findRow('20261010-123456')
    expect(within(row).getByText('2026/10/10 10:00')).toBeInTheDocument()
    expect(within(row).getByText('山田 太郎')).toBeInTheDocument()
    expect(within(row).getByText('4,300 円')).toBeInTheDocument()
    expect(within(row).getByText('受付')).toBeInTheDocument()
    const requested = getRow('20261009-654321')
    expect(within(requested).getByText('キャンセル申請中')).toBeInTheDocument()
  })

  test('AC-98 注文番号・注文日の範囲で絞り込むと、URL に残し、その条件で取得し直す', async () => {
    const queries = serveOrders()
    const { user, router } = renderApp('/orders')
    await screen.findByRole('link', { name: '20261010-123456' })

    await user.type(screen.getByLabelText('注文番号（前方一致）'), '20261010')
    await user.type(screen.getByLabelText('注文日（から）'), '2026-10-01')
    await user.type(screen.getByLabelText('注文日（まで）'), '2026-10-10')
    await user.click(screen.getByRole('button', { name: '絞り込む' }))

    expect(router.state.location.search).toBe('?orderNumber=20261010&orderedFrom=2026-10-01&orderedTo=2026-10-10')
    await waitFor(() => expect(queries.at(-1)?.get('orderNumber')).toBe('20261010'))
    expect(queries.at(-1)?.get('orderedFrom')).toBe('2026-10-01')
    expect(queries.at(-1)?.get('orderedTo')).toBe('2026-10-10')
  })

  test('AC-98 URL の状態の条件で開くと、その状態で取得する', async () => {
    const queries = serveOrders([orderSummary({ status: 'CANCELLATION_REQUESTED' })])
    renderApp('/orders?status=CANCELLATION_REQUESTED')

    expect(await screen.findByRole('link', { name: '20261010-123456' })).toBeInTheDocument()
    expect(queries.at(-1)?.get('status')).toBe('CANCELLATION_REQUESTED')
  })

  test('AC-160 支払の状態の列を出す（決済を入れる前の注文は空）', async () => {
    serveOrders([
      orderSummary({ paymentStatus: 'AUTHORIZED' }),
      orderSummary({
        id: '00000000-0000-0000-0000-000000000502',
        orderNumber: '20261009-654321',
        status: 'PENDING_PAYMENT',
        paymentStatus: 'UNPAID',
      }),
      orderSummary({ id: '00000000-0000-0000-0000-000000000503', orderNumber: '20261001-000001' }),
    ])
    renderApp('/orders')

    expect(await screen.findByRole('columnheader', { name: '支払' })).toBeInTheDocument()
    const authorized = await findRow('20261010-123456')
    expect(within(authorized).getByText('与信済み')).toBeInTheDocument()
    const pending = getRow('20261009-654321')
    expect(within(pending).getByText('支払待ち')).toBeInTheDocument()
    expect(within(pending).getByText('未払い')).toBeInTheDocument()
    expect(getCell('20261001-000001', '支払')).toHaveTextContent(/^$/)
  })

  test('AC-161 状態「支払待ち」で絞り込むと、URL に残し、その状態で取得し直す', async () => {
    const queries = serveOrders()
    const { user, router } = renderApp('/orders')
    await screen.findByRole('link', { name: '20261010-123456' })

    await user.click(screen.getByLabelText('状態'))
    await user.click(await screen.findByRole('option', { name: '支払待ち' }))
    await user.click(screen.getByRole('button', { name: '絞り込む' }))

    expect(router.state.location.search).toBe('?status=PENDING_PAYMENT')
    await waitFor(() => expect(queries.at(-1)?.get('status')).toBe('PENDING_PAYMENT'))
  })

  test('該当する注文がなければ、そう示す', async () => {
    serveOrders([])
    renderApp('/orders')

    expect(await screen.findByText('該当する注文はありません。')).toBeInTheDocument()
  })
})

describe('出荷と与信期限', () => {
  test('AC-182、186 配送状態と、与信期限が近いことを出す', async () => {
    serveOrders([
      orderSummary({ authorizationExpiring: true, paymentStatus: 'AUTHORIZED' }),
      orderSummary({
        id: '00000000-0000-0000-0000-000000000502',
        orderNumber: '20261009-654321',
        status: 'SHIPPED',
        paymentStatus: 'CAPTURED',
        shipmentStatus: 'RETURNED',
      }),
    ])
    renderApp('/orders')

    const expiring = await findRow('20261010-123456')
    expect(within(expiring).getByText('期限が近い')).toBeInTheDocument()
    const shipped = getRow('20261009-654321')
    expect(within(shipped).getByText('出荷済み')).toBeInTheDocument()
    expect(within(shipped).getByText('返送')).toBeInTheDocument()
    expect(within(shipped).queryByText('期限が近い')).not.toBeInTheDocument()
  })

  test('AC-182 状態「出荷済み」で絞り込める', async () => {
    const queries = serveOrders()
    const { user, router } = renderApp('/orders')
    await screen.findByRole('link', { name: '20261010-123456' })

    await user.click(screen.getByRole('combobox', { name: '状態' }))
    await user.click(await screen.findByRole('option', { name: '出荷済み' }))
    await user.click(screen.getByRole('button', { name: '絞り込む' }))

    expect(router.state.location.search).toBe('?status=SHIPPED')
    await waitFor(() => expect(queries.at(-1)?.get('status')).toBe('SHIPPED'))
  })

  test('AC-187、189 与信期限が近い注文と、返送の注文で絞り込める', async () => {
    const queries = serveOrders()
    const { user, router } = renderApp('/orders')
    await screen.findByRole('link', { name: '20261010-123456' })

    await user.click(screen.getByRole('checkbox', { name: '与信期限が近い' }))
    await user.click(screen.getByRole('checkbox', { name: '返送' }))
    await user.click(screen.getByRole('button', { name: '絞り込む' }))

    expect(router.state.location.search).toBe('?authorizationExpiring=true&returned=true')
    await waitFor(() => expect(queries.at(-1)?.get('authorizationExpiring')).toBe('true'))
    expect(queries.at(-1)?.get('returned')).toBe('true')
  })
})
