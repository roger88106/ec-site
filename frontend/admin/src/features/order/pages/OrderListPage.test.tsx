import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { orderSummary } from '@/test/fixtures'
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
  it('AC-97 注文番号、注文日時、氏名、総額、状態を出す', async () => {
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

    const row = (await screen.findByRole('link', { name: '20261010-123456' })).closest('tr') as HTMLElement
    expect(within(row).getByText('2026/10/10 10:00')).toBeInTheDocument()
    expect(within(row).getByText('山田 太郎')).toBeInTheDocument()
    expect(within(row).getByText('4,300 円')).toBeInTheDocument()
    expect(within(row).getByText('受付')).toBeInTheDocument()
    const requested = screen.getByRole('link', { name: '20261009-654321' }).closest('tr') as HTMLElement
    expect(within(requested).getByText('キャンセル申請中')).toBeInTheDocument()
  })

  it('AC-98 注文番号・注文日の範囲で絞り込むと、URL に残し、その条件で取得し直す', async () => {
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

  it('AC-98 URL の状態の条件で開くと、その状態で取得する', async () => {
    const queries = serveOrders([orderSummary({ status: 'CANCELLATION_REQUESTED' })])
    renderApp('/orders?status=CANCELLATION_REQUESTED')

    expect(await screen.findByRole('link', { name: '20261010-123456' })).toBeInTheDocument()
    expect(queries.at(-1)?.get('status')).toBe('CANCELLATION_REQUESTED')
  })

  it('該当する注文がなければ、そう示す', async () => {
    serveOrders([])
    renderApp('/orders')

    expect(await screen.findByText('該当する注文はありません。')).toBeInTheDocument()
  })
})
