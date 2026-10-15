import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SalesTrendReport } from '@/api/types'
import { renderApp } from '@/test/renderApp'
import { SHIRT_ID, serveReportBasics } from '@/test/reportServer'
import { http, server } from '@/test/server'

/** 2026 年度の 4 月〜6 月の月ごとの推移（5 月は売れなかった） */
const QUARTER_BY_MONTH: SalesTrendReport = {
  points: [
    { start: '2026-04-01', end: '2026-04-30', amount: 120000, quantity: 40 },
    { start: '2026-05-01', end: '2026-05-31', amount: 0, quantity: 0 },
    { start: '2026-06-01', end: '2026-06-30', amount: 98000, quantity: 31 },
  ],
  shippingExcluded: false,
}

/**
 * 売上の推移の API に応答する。受け取ったクエリは queries に取っておく。
 *
 * @param report 返す推移
 */
function serveTrend(report: SalesTrendReport = QUARTER_BY_MONTH) {
  serveReportBasics()
  const queries: URLSearchParams[] = []
  server.use(
    http.get('/reports/sales-trend', ({ request, response }) => {
      queries.push(new URL(request.url).searchParams)
      return response(200).json(report)
    }),
  )
  return queries
}

afterEach(() => {
  vi.useRealTimers()
})

describe('売上の推移', () => {
  it('AC-197、201 区切りごとの売上額と販売数量を表に出し、売れなかった区切りも 0 の行で出す', async () => {
    serveTrend()
    renderApp('/reports/sales-trend?from=2026-04-01&to=2026-06-30')

    const row = (await screen.findByRole('cell', { name: '2026 年 5 月' })).closest('tr') as HTMLElement
    expect(within(row).getByText('0 円')).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '2026 年 4 月' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '120,000 円' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '40' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '売上額の推移' })).toBeInTheDocument()
  })

  it('AC-197 URL に期間がなければ、今年度を月ごとに出す', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-15T03:00:00Z'))
    const queries = serveTrend()
    renderApp('/reports/sales-trend')

    await screen.findByRole('cell', { name: '2026 年 4 月' })
    expect(queries[0].get('from')).toBe('2026-04-01')
    expect(queries[0].get('to')).toBe('2027-03-31')
    expect(queries[0].get('granularity')).toBe('MONTH')
    expect(queries[0].get('basis')).toBe('ORDER')
  })

  it('AC-197、200 区切りと売上の数え方を変えて表示すると、条件を URL に書き、その条件で取り直す', async () => {
    const queries = serveTrend()
    const { user, router } = renderApp('/reports/sales-trend?from=2026-04-01&to=2026-06-30')
    await screen.findByRole('cell', { name: '2026 年 4 月' })

    await user.click(screen.getByRole('combobox', { name: '区切り' }))
    await user.click(await screen.findByRole('option', { name: '四半期' }))
    await user.click(screen.getByRole('combobox', { name: '売上の数え方' }))
    await user.click(await screen.findByRole('option', { name: '出荷基準' }))
    await user.click(screen.getByRole('checkbox', { name: '消費税を除く' }))
    await user.click(screen.getByRole('button', { name: '表示する' }))

    await waitFor(() =>
      expect(router.state.location.search).toBe(
        '?from=2026-04-01&to=2026-06-30&granularity=QUARTER&basis=SHIPMENT&excludeTax=true',
      ),
    )
    await waitFor(() => expect(queries.at(-1)?.get('basis')).toBe('SHIPMENT'))
    expect(queries.at(-1)?.get('granularity')).toBe('QUARTER')
    expect(queries.at(-1)?.get('excludeTax')).toBe('true')
  })

  it('BR-24 期間の選択肢の前年度を選ぶと、始まりと終わりの日を埋める', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-15T03:00:00Z'))
    serveTrend()
    const { user, router } = renderApp('/reports/sales-trend?from=2026-04-01&to=2026-06-30')
    await screen.findByRole('cell', { name: '2026 年 4 月' })

    await user.click(screen.getByRole('combobox', { name: '期間の選択肢' }))
    await user.click(await screen.findByRole('option', { name: '前年度' }))
    await user.click(screen.getByRole('button', { name: '表示する' }))

    await waitFor(() => expect(router.state.location.search).toBe('?from=2025-04-01&to=2026-03-31'))
  })

  it('AC-199 商品を選ぶと、その商品で取り直し、送料を含めていないことを示す', async () => {
    const queries = serveTrend({ ...QUARTER_BY_MONTH, shippingExcluded: true })
    renderApp(`/reports/sales-trend?from=2026-04-01&to=2026-06-30&productId=${SHIRT_ID}`)

    expect(await screen.findByText('送料を含めていません。')).toBeInTheDocument()
    expect(queries[0].get('productId')).toBe(SHIRT_ID)
    expect(screen.getByRole('combobox', { name: '商品' })).toHaveTextContent('ベーシック T シャツ')
  })

  it('AC-218 表を CSV で取り出せる', async () => {
    serveTrend()
    const createObjectURL = vi.fn(() => 'blob:report')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }))
    const { user } = renderApp('/reports/sales-trend?from=2026-04-01&to=2026-06-30')
    await screen.findByRole('cell', { name: '2026 年 4 月' })

    await user.click(screen.getByRole('button', { name: 'CSV で取り出す' }))

    const blob = (createObjectURL.mock.calls[0] as unknown as [Blob])[0]
    const text = new TextDecoder('utf-8', { ignoreBOM: true }).decode(await blob.arrayBuffer())
    expect(text).toBe(
      '\uFEFF区切り,売上額,販売数量\r\n2026 年 4 月,120000,40\r\n2026 年 5 月,0,0\r\n2026 年 6 月,98000,31\r\n',
    )
    vi.unstubAllGlobals()
  })
})
