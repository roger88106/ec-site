import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import type { VariantSalesReport } from '@/api/types'
import { recordDownloads } from '@/test/download'
import { SHIRT_ID } from '@/test/fixtures'
import { findRow, getRow } from '@/test/queries'
import { renderApp } from '@/test/renderApp'
import { serveReportBasics } from '@/test/reportServer'
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

/** バリアントの売れ行きの API に応答する。受け取ったクエリは queries に取っておく */
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

describe('バリアントの売れ行き', () => {
  test('AC-202、203、204 バリアントごとの販売数量と売上額を、販売数量の多い順に出し、売れなかったものも 0 で出す', async () => {
    const queries = serveVariantSales()
    renderApp(`/reports/variant-sales?from=2026-09-01&to=2026-09-30&productId=${SHIRT_ID}&excludeTax=true`)

    const white = await findRow('色：白、サイズ：M')
    const black = getRow('色：黒、サイズ：L')
    expect(within(white).getByRole('cell', { name: '12' })).toBeInTheDocument()
    expect(within(white).getByRole('cell', { name: '39,336 円' })).toBeInTheDocument()
    expect(within(black).getByRole('cell', { name: '0 円' })).toBeInTheDocument()
    expect(screen.getAllByRole('row').slice(1)).toEqual([white, black])
    expect(screen.getByText('商品：ベーシック T シャツ')).toBeInTheDocument()
    expect(queries).toHaveLength(1)
    const [query] = queries
    expect(query.get('productId')).toBe(SHIRT_ID)
    expect(query.get('excludeTax')).toBe('true')
  })

  test('AC-202 商品を選ぶまでは API を呼ばず、選ぶと URL に書いて出す', async () => {
    const queries = serveVariantSales()
    const { user, router } = renderApp('/reports/variant-sales?from=2026-09-01&to=2026-09-30')

    expect(await screen.findByText('商品を選ぶと、バリアントごとの売れ行きを出します。')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: '送料を除く' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: '商品' }))
    await user.click(await screen.findByRole('option', { name: 'ベーシック T シャツ' }))
    await user.click(screen.getByRole('button', { name: '表示する' }))

    await waitFor(() =>
      expect(router.state.location.search).toBe(`?from=2026-09-01&to=2026-09-30&productId=${SHIRT_ID}`),
    )
    expect(await screen.findByRole('cell', { name: '色：白、サイズ：M' })).toBeInTheDocument()
    expect(queries).toHaveLength(1)
  })

  test('AC-218 表を CSV で取り出せる', async () => {
    serveVariantSales()
    const recorder = recordDownloads()
    const { user } = renderApp(`/reports/variant-sales?from=2026-09-01&to=2026-09-30&productId=${SHIRT_ID}`)
    await screen.findByRole('cell', { name: '色：白、サイズ：M' })

    await user.click(screen.getByRole('button', { name: 'CSV で取り出す' }))

    const downloads = await recorder.downloads()
    expect(downloads).toHaveLength(1)
    const [download] = downloads
    expect(download.filename).toBe('バリアントの売れ行き_ベーシック T シャツ_2026-09-01_2026-09-30.csv')
    expect(download.text).toBe(
      '\uFEFFバリアント,品番,販売数量,売上額\r\n色：白、サイズ：M,TEE-001-WH-M,12,39336\r\n色：黒、サイズ：L,TEE-001-BK-L,0,0\r\n',
    )
  })
})
