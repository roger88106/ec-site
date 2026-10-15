import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { VariantSalesReport } from '@/api/types'
import { SHIRT_ID, serveReportBasics } from '@/features/report/pages/reportTestServer'
import { renderApp } from '@/test/renderApp'
import { http, server } from '@/test/server'

/** T シャツの売れ行き（販売数量の多い順。黒の L は売れなかった） */
const SHIRT_SALES: VariantSalesReport = {
  productId: SHIRT_ID,
  productName: 'ベーシック T シャツ',
  variants: [
    {
      variantId: '00000000-0000-0000-0000-000000003001',
      variantCode: 'TEE-001-WH-M',
      axisValues: [
        { name: '色', value: '白' },
        { name: 'サイズ', value: 'M' },
      ],
      quantity: 12,
      amount: 39336,
    },
    {
      variantId: '00000000-0000-0000-0000-000000003004',
      variantCode: 'TEE-001-BK-L',
      axisValues: [
        { name: '色', value: '黒' },
        { name: 'サイズ', value: 'L' },
      ],
      quantity: 0,
      amount: 0,
    },
  ],
  shippingExcluded: true,
}

/** バリエーションの売れ行きの API に応答する。受け取ったクエリは queries に取っておく */
function serveVariantSales() {
  serveReportBasics()
  const queries: URLSearchParams[] = []
  server.use(
    http.get('/reports/variant-sales', ({ request, response }) => {
      queries.push(new URL(request.url).searchParams)
      return response(200).json(SHIRT_SALES)
    }),
  )
  return queries
}

describe('バリエーションの売れ行き', () => {
  it('AC-202〜204 バリエーションごとの販売数量と売上額を、販売数量の多い順に出し、売れなかったものも 0 で出す', async () => {
    const queries = serveVariantSales()
    renderApp(`/reports/variant-sales?from=2026-09-01&to=2026-09-30&productId=${SHIRT_ID}&excludeTax=true`)

    const rows = (await screen.findAllByRole('row')).slice(1)
    expect(within(rows[0]).getByRole('cell', { name: '白 / M' })).toBeInTheDocument()
    expect(within(rows[0]).getByRole('cell', { name: '12' })).toBeInTheDocument()
    expect(within(rows[0]).getByRole('cell', { name: '39,336 円' })).toBeInTheDocument()
    expect(within(rows[1]).getByRole('cell', { name: '黒 / L' })).toBeInTheDocument()
    expect(within(rows[1]).getByRole('cell', { name: '0 円' })).toBeInTheDocument()
    expect(screen.getByText('商品：ベーシック T シャツ')).toBeInTheDocument()
    expect(queries[0].get('productId')).toBe(SHIRT_ID)
    expect(queries[0].get('excludeTax')).toBe('true')
  })

  it('AC-202 商品を選ぶまでは API を呼ばず、選ぶと URL に書いて出す', async () => {
    const queries = serveVariantSales()
    const { user, router } = renderApp('/reports/variant-sales?from=2026-09-01&to=2026-09-30')

    expect(await screen.findByText('商品を選ぶと、バリエーションごとの売れ行きを出します。')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: '送料を除く' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: '商品' }))
    await user.click(await screen.findByRole('option', { name: 'ベーシック T シャツ' }))
    await user.click(screen.getByRole('button', { name: '表示する' }))

    await waitFor(() =>
      expect(router.state.location.search).toBe(`?from=2026-09-01&to=2026-09-30&productId=${SHIRT_ID}`),
    )
    expect(await screen.findByRole('cell', { name: '白 / M' })).toBeInTheDocument()
    expect(queries).toHaveLength(1)
  })
})
