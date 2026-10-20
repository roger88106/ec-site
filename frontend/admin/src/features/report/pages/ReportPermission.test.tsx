import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '@/test/renderApp'
import { serveReportBasics } from '@/test/reportServer'
import { http, server } from '@/test/server'

/** 閲覧者の権限（参照だけ） */
const VIEWER = ['tax:read', 'category:read', 'product:read', 'inventory:read', 'order:read']

/** 営業の権限（参照と、売上の推移を除くレポート） */
const SALES = [...VIEWER, 'report-variant-sales:read', 'report-customer:read']

describe('レポートの権限による出し分け', () => {
  it('AC-214 営業は、メニューの「レポート」に、権限のある画面だけを見る', async () => {
    renderApp('/reports/sales-trend', { permissions: SALES })

    const reports = await screen.findByText('レポート')
    expect(reports).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'バリアントの売れ行き' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '地域・年齢層・性別' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '売上の推移' })).not.toBeInTheDocument()
  })

  it('AC-214 権限のない画面を URL で開くと「権限がない」を示す', async () => {
    renderApp('/reports/sales-trend', { permissions: SALES })

    expect(await screen.findByText('この画面を見る権限がありません。')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '売上の推移' })).not.toBeInTheDocument()
  })

  it('AC-214 レポートの権限がなければ、メニューに「レポート」を出さない', async () => {
    renderApp('/reports/customer-breakdown', { permissions: VIEWER })

    expect(await screen.findByText('この画面を見る権限がありません。')).toBeInTheDocument()
    expect(screen.queryByText('レポート')).not.toBeInTheDocument()
  })

  it('AC-214 権限があれば、メニューから画面を開ける', async () => {
    serveReportBasics()
    server.use(
      http.get('/reports/customer-breakdown', ({ response }) =>
        response(200).json({ prefectures: [], ageGroups: [], genders: [], shippingExcluded: false }),
      ),
    )
    const { user, router } = renderApp('/reports/sales-trend', { permissions: SALES })

    await user.click(await screen.findByRole('link', { name: '地域・年齢層・性別' }))

    expect(router.state.location.pathname).toBe('/reports/customer-breakdown')
    expect(await screen.findByRole('heading', { name: '地域・年齢層・性別' })).toBeInTheDocument()
  })
})
