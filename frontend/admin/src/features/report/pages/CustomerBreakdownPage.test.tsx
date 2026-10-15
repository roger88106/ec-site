import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { CustomerBreakdownReport } from '@/api/types'
import { renderApp } from '@/test/renderApp'
import { SHIRT_ID, serveReportBasics } from '@/test/reportServer'
import { http, server } from '@/test/server'

/** 東京都・大阪府・不明の 3 つの都道府県と、すべての年齢層と性別 */
const BREAKDOWN: CustomerBreakdownReport = {
  prefectures: [
    { prefectureCode: '13', amount: 52000, orders: 12 },
    { prefectureCode: '27', amount: 31000, orders: 7 },
    { amount: 4000, orders: 1 },
  ],
  ageGroups: [
    { ageGroup: 'TEENS_OR_YOUNGER', amount: 0, orders: 0 },
    { ageGroup: 'TWENTIES', amount: 21000, orders: 5 },
    { ageGroup: 'THIRTIES', amount: 30000, orders: 6 },
    { ageGroup: 'FORTIES', amount: 18000, orders: 4 },
    { ageGroup: 'FIFTIES', amount: 9000, orders: 2 },
    { ageGroup: 'SIXTIES_OR_OLDER', amount: 5000, orders: 1 },
    { ageGroup: 'UNKNOWN', amount: 4000, orders: 2 },
  ],
  genders: [
    { gender: 'MALE', amount: 40000, orders: 9 },
    { gender: 'FEMALE', amount: 41000, orders: 9 },
    { gender: 'OTHER', amount: 2000, orders: 1 },
    { gender: 'UNKNOWN', amount: 4000, orders: 1 },
  ],
  shippingExcluded: false,
}

/** 地域・年齢層・性別の API に応答する。受け取ったクエリは queries に取っておく */
function serveBreakdown(report: CustomerBreakdownReport = BREAKDOWN) {
  serveReportBasics()
  const queries: URLSearchParams[] = []
  server.use(
    http.get('/reports/customer-breakdown', ({ request, response }) => {
      queries.push(new URL(request.url).searchParams)
      return response(200).json(report)
    }),
  )
  return queries
}

/** 分け方の表の、データの行 */
function rowsOf(dimension: string): HTMLElement[] {
  const section = screen.getByRole('region', { name: `${dimension}ごとの売上額` })
  return within(section).getAllByRole('row').slice(1)
}

describe('地域・年齢層・性別', () => {
  it('AC-205、220 都道府県ごとの売上額と注文件数を、コードの順に名前で出し、コードのないものは不明', async () => {
    serveBreakdown()
    renderApp('/reports/customer-breakdown?from=2026-09-01&to=2026-09-30')

    await screen.findByRole('region', { name: '都道府県ごとの売上額' })
    const rows = rowsOf('都道府県')
    expect(rows.map((row) => within(row).getAllByRole('cell')[0].textContent)).toEqual(['東京都', '大阪府', '不明'])
    expect(within(rows[0]).getByRole('cell', { name: '52,000 円' })).toBeInTheDocument()
    expect(within(rows[0]).getByRole('cell', { name: '12' })).toBeInTheDocument()
  })

  it('AC-206、208、219 年齢層と性別は、すべての区分（不明を含む）を出す', async () => {
    serveBreakdown()
    renderApp('/reports/customer-breakdown?from=2026-09-01&to=2026-09-30')

    await screen.findByRole('region', { name: '年齢層ごとの売上額' })
    expect(rowsOf('年齢層').map((row) => within(row).getAllByRole('cell')[0].textContent)).toEqual([
      '10 代以下',
      '20 代',
      '30 代',
      '40 代',
      '50 代',
      '60 代以上',
      '不明',
    ])
    expect(rowsOf('性別').map((row) => within(row).getAllByRole('cell')[0].textContent)).toEqual([
      '男性',
      '女性',
      'その他',
      '不明',
    ])
  })

  it('AC-207 商品を選ぶと、その商品で取り直し、送料を含めていないことを示す', async () => {
    const queries = serveBreakdown({ ...BREAKDOWN, shippingExcluded: true })
    renderApp(`/reports/customer-breakdown?from=2026-09-01&to=2026-09-30&productId=${SHIRT_ID}&basis=SHIPMENT`)

    expect(await screen.findByText('送料を含めていません。')).toBeInTheDocument()
    expect(queries[0].get('productId')).toBe(SHIRT_ID)
    expect(queries[0].get('basis')).toBe('SHIPMENT')
  })
})
